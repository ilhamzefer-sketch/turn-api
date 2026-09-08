package az.turn.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class EpointWalletPaymentService {
    private static final Logger log = LoggerFactory.getLogger(EpointWalletPaymentService.class);
    private static final String PROVIDER = "epoint";
    private static final String CALLBACK_PATH = "/api/payments/epoint/callback";

    private final EpointProperties properties;
    private final WalletTopUpRequestRepository requestRepository;
    private final WalletTopUpCreditService creditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String publicBaseUrl;
    private final String callbackBaseUrl;

    public EpointWalletPaymentService(
            EpointProperties properties,
            WalletTopUpRequestRepository requestRepository,
            WalletTopUpCreditService creditService,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.public-base-url:https://novbetime.az}") String publicBaseUrl,
            @Value("${app.payment.callback-base-url:http://127.0.0.1:8080}") String callbackBaseUrl
    ) {
        this.properties = properties;
        this.requestRepository = requestRepository;
        this.creditService = creditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    @Transactional
    public WalletTopUpRequestEntity start(WalletTopUpRequestEntity request) {
        requireConfiguration();
        String orderId = orderIdFor(requireRequestId(request), clock);
        try {
            String redirectUrl = createCheckoutUrl(request, orderId);
            request.startExternalPayment(PROVIDER, orderId, redirectUrl, LocalDateTime.now(clock));
            return requestRepository.saveAndFlush(request);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Epoint wallet checkout request failed for top-up {}", request.getId(), exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Epoint checkout yaradıla bilmədi.", exception);
        }
    }

    public boolean isConfigured() {
        return !isBlank(properties.publicKey()) && !isBlank(properties.privateKey());
    }

    @Transactional
    public void processCallback(String data, String signature) {
        requireConfiguration();
        if (!EpointSignature.isValid(data, signature, properties.privateKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint callback imzası yanlışdır.");
        }

        Map<String, Object> callback = decodeCallback(data);
        String orderId = normalizeRequired(callback.get("order_id"), "Epoint order id bosdur.");
        long requestId = requestIdFromOrderId(orderId);
        WalletTopUpRequestEntity request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Balans artırma sorğusu tapılmadı."));

        requireEpointRequest(request, orderId);
        String providerStatus = normalizeRequired(callback.get("status"), "Epoint status boşdur.");
        String providerReference = providerReference(callback);

        if (request.getStatus() == WalletTopUpRequestStatus.PAID
                || request.getStatus() == WalletTopUpRequestStatus.PAYMENT_FAILED) {
            return;
        }
        if (request.getStatus() != WalletTopUpRequestStatus.AWAITING_RECEIPT
                && request.getStatus() != WalletTopUpRequestStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ödəniş sorğusunun statusu uyğun deyil.");
        }

        if (!"success".equalsIgnoreCase(providerStatus)) {
            if (request.getStatus() == WalletTopUpRequestStatus.EXPIRED) {
                return;
            }
            request.failExternalPayment(providerReference, providerStatus, LocalDateTime.now(clock));
            requestRepository.saveAndFlush(request);
            return;
        }

        requireMatchingAmount(callback, request);
        WalletTransactionEntity transaction = creditService.creditExternalPayment(request, PROVIDER);
        request.completeExternalPayment(providerReference, providerStatus, transaction, LocalDateTime.now(clock));
        requestRepository.saveAndFlush(request);
    }

    private String createCheckoutUrl(WalletTopUpRequestEntity request, String orderId) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("public_key", properties.publicKey());
        payload.put("amount", BigDecimal.valueOf(request.getAmountAzn()).stripTrailingZeros().toPlainString());
        payload.put("currency", currency());
        payload.put("language", language());
        payload.put("order_id", orderId);
        payload.put("description", "NovbeTime wallet top-up #" + request.getId());
        payload.put("success_redirect_url", successUrl());
        payload.put("error_redirect_url", errorUrl());
        payload.put("result_url", resultUrl());

        String encodedData = Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(payload));
        String signedData = EpointSignature.sign(encodedData, properties.privateKey());
        Map<String, Object> response = postForm(apiBaseUrl() + "/request", encodedData, signedData);
        String redirectUrl = response == null ? null : Objects.toString(response.get("redirect_url"), null);
        if (redirectUrl == null || redirectUrl.isBlank() || "null".equalsIgnoreCase(redirectUrl)) {
            throw new IOException("Epoint did not return checkout URL. " + responseSummary(response));
        }
        return redirectUrl;
    }

    private Map<String, Object> postForm(String endpoint, String data, String signature) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);

        String body = "data=" + URLEncoder.encode(data, StandardCharsets.UTF_8)
                + "&signature=" + URLEncoder.encode(signature, StandardCharsets.UTF_8);
        connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        int status = connection.getResponseCode();
        try (InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream()) {
            if (stream == null) {
                throw new IOException("Epoint returned HTTP " + status + ".");
            }
            Map<String, Object> response = objectMapper.readValue(stream, new TypeReference<>() {});
            if (status < 200 || status >= 300) {
                throw new IOException("Epoint returned HTTP " + status + ". " + responseSummary(response));
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private String responseSummary(Map<String, Object> response) {
        if (response == null || response.isEmpty()) {
            return "Response was empty.";
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (String key : new String[]{"status", "message", "error", "errors", "code"}) {
            if (response.containsKey(key)) {
                safe.put(key, response.get(key));
            }
        }
        return safe.isEmpty() ? "Response keys: " + response.keySet() : "Response: " + safe;
    }

    private Map<String, Object> decodeCallback(String data) {
        try {
            return objectMapper.readValue(Base64.getDecoder().decode(data), new TypeReference<>() {});
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint callback data oxuna bilmədi.", exception);
        }
    }

    private void requireEpointRequest(WalletTopUpRequestEntity request, String orderId) {
        if (!PROVIDER.equalsIgnoreCase(request.getPaymentProvider())
                || !orderId.equals(request.getExternalOrderId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Epoint order sorğu ilə uyğun deyil.");
        }
    }

    private void requireMatchingAmount(Map<String, Object> callback, WalletTopUpRequestEntity request) {
        Object suppliedAmount = callback.get("amount");
        Object suppliedCurrency = callback.get("currency");
        if (suppliedAmount == null) {
            return;
        }
        BigDecimal actualAmount;
        try {
            actualAmount = new BigDecimal(String.valueOf(suppliedAmount));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint məbləği düzgün deyil.", exception);
        }
        if (actualAmount.compareTo(BigDecimal.valueOf(request.getAmountAzn())) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Epoint məbləği sorğu ilə uyğun deyil.");
        }
        if (suppliedCurrency != null && !currency().equalsIgnoreCase(String.valueOf(suppliedCurrency))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Epoint məbləği sorğu ilə uyğun deyil.");
        }
    }

    private String providerReference(Map<String, Object> callback) {
        for (String key : new String[]{"transaction", "transaction_id", "payment_id", "bank_transaction", "rrn"}) {
            Object value = callback.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "EPOINT-" + normalizeRequired(callback.get("order_id"), "Epoint order id boşdur.");
    }

    private long parseRequiredLong(Map<String, Object> callback, String key) {
        String value = normalizeRequired(callback.get(key), "Epoint " + key + " boşdur.");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint " + key + " düzgün deyil.", exception);
        }
    }

    private String normalizeRequired(Object value, String message) {
        String normalized = value == null ? null : String.valueOf(value).trim();
        if (normalized == null || normalized.isEmpty() || "null".equalsIgnoreCase(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    static String orderIdFor(long requestId, Clock clock) {
        if (requestId <= 0) {
            throw new IllegalArgumentException("Balans artirma sorgusu saxlanilmis olmalidir.");
        }
        return "wallet-" + requestId + "-" + Instant.now(clock).toEpochMilli();
    }

    static long requestIdFromOrderId(String orderId) {
        String normalized = orderId == null ? "" : orderId.trim();
        if (normalized.matches("\\d+")) {
            return Long.parseLong(normalized);
        }
        String prefix = "wallet-";
        if (!normalized.startsWith(prefix)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint order id duzgun deyil.");
        }
        int idStart = prefix.length();
        int idEnd = normalized.indexOf('-', idStart);
        if (idEnd <= idStart) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint order id duzgun deyil.");
        }
        try {
            return Long.parseLong(normalized.substring(idStart, idEnd));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint order id duzgun deyil.", exception);
        }
    }

    private void requireConfiguration() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Epoint konfiqurasiyası tamamlanmayıb.");
        }
    }

    private Long requireRequestId(WalletTopUpRequestEntity request) {
        if (request == null || request.getId() == null) {
            throw new IllegalArgumentException("Balans artırma sorğusu saxlanılmış olmalıdır.");
        }
        return request.getId();
    }

    private String apiBaseUrl() {
        return isBlank(properties.apiBaseUrl()) ? "https://epoint.az/api/1" : trimTrailingSlash(properties.apiBaseUrl());
    }

    private String successUrl() {
        return isBlank(properties.successUrl()) ? trimTrailingSlash(publicBaseUrl) + "/wallet?payment=success" : properties.successUrl();
    }

    private String errorUrl() {
        return isBlank(properties.errorUrl()) ? trimTrailingSlash(publicBaseUrl) + "/wallet?payment=failed" : properties.errorUrl();
    }

    private String resultUrl() {
        return isBlank(properties.resultUrl()) ? trimTrailingSlash(callbackBaseUrl) + CALLBACK_PATH : properties.resultUrl();
    }

    private String language() {
        return isBlank(properties.language()) ? "az" : properties.language().trim().toLowerCase(Locale.ROOT);
    }

    private String currency() {
        return isBlank(properties.currency()) ? "AZN" : properties.currency().trim().toUpperCase(Locale.ROOT);
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
