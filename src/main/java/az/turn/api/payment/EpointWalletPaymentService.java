package az.turn.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
    private final EpointClient client;
    private final WalletTopUpCheckoutService checkoutService;
    private final String publicBaseUrl;
    private final String callbackBaseUrl;

    public EpointWalletPaymentService(
            EpointProperties properties,
            WalletTopUpRequestRepository requestRepository,
            WalletTopUpCreditService creditService,
            ObjectMapper objectMapper,
            Clock clock,
            EpointClient client,
            WalletTopUpCheckoutService checkoutService,
            @Value("${app.public-base-url:https://novbetime.az}") String publicBaseUrl,
            @Value("${app.payment.callback-base-url:http://127.0.0.1:8080}") String callbackBaseUrl
    ) {
        this.properties = properties;
        this.requestRepository = requestRepository;
        this.creditService = creditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.client = client;
        this.checkoutService = checkoutService;
        this.publicBaseUrl = publicBaseUrl;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public WalletTopUpRequestDto start(WalletTopUpRequestDto request) {
        requireConfiguration();
        try {
            Map<String, Object> response = createCheckout(request);
            String redirectUrl = normalizeRequired(response.get("redirect_url"), "Epoint checkout URL is missing.");
            URI redirect = URI.create(redirectUrl);
            if (!"success".equals(response.get("status")) || !"https".equalsIgnoreCase(redirect.getScheme())
                    || redirect.getHost() == null || redirect.getUserInfo() != null || redirectUrl.length() > 500) {
                throw new IOException("Epoint returned an invalid checkout response.");
            }
            String transaction = normalizeRequired(response.get("transaction"), "Epoint transaction is missing.");
            if (transaction.length() > 180) {
                throw new IOException("Epoint transaction is too long.");
            }
            return checkoutService.finish(request.id(), redirectUrl, transaction);
        } catch (Exception exception) {
            log.warn("Epoint checkout outcome is unknown for top-up {} ({})", request.id(), exception.getClass().getSimpleName());
            return checkoutService.unknown(request.id());
        }
    }

    public boolean isConfigured() {
        return !isBlank(properties.publicKey()) && !isBlank(properties.privateKey())
                && (isBlank(properties.currency()) || "AZN".equalsIgnoreCase(properties.currency().trim()));
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

        if (request.getExternalCheckoutTransactionId() != null
                && !request.getExternalCheckoutTransactionId().equals(providerReference)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Epoint transaction sorğu ilə uyğun deyil.");
        }
        if ("success".equalsIgnoreCase(providerStatus)) {
            requireMatchingAmount(callback, request);
        }
        if (request.getStatus() == WalletTopUpRequestStatus.PAID) {
            return;
        }
        if (request.getStatus() != WalletTopUpRequestStatus.AWAITING_RECEIPT
                && request.getStatus() != WalletTopUpRequestStatus.EXPIRED
                && request.getStatus() != WalletTopUpRequestStatus.SUPERSEDED
                && request.getStatus() != WalletTopUpRequestStatus.PAYMENT_FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ödəniş sorğusunun statusu uyğun deyil.");
        }
        request.bindCheckoutTransaction(providerReference);
        if (!"success".equalsIgnoreCase(providerStatus)) {
            if (("failed".equalsIgnoreCase(providerStatus) || "error".equalsIgnoreCase(providerStatus))
                    && request.getStatus() == WalletTopUpRequestStatus.AWAITING_RECEIPT) {
                request.failExternalPayment(providerReference, providerStatus.toLowerCase(Locale.ROOT), LocalDateTime.now(clock));
            }
            requestRepository.saveAndFlush(request);
            return;
        }

        WalletTransactionEntity transaction = creditService.creditExternalPayment(request, PROVIDER);
        request.completeExternalPayment(providerReference, providerStatus, transaction, LocalDateTime.now(clock));
        requestRepository.saveAndFlush(request);
    }

    private Map<String, Object> createCheckout(WalletTopUpRequestDto request) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("public_key", properties.publicKey());
        payload.put("amount", request.amountAzn().stripTrailingZeros().toPlainString());
        payload.put("currency", request.currency());
        payload.put("language", language());
        payload.put("order_id", request.externalOrderId());
        payload.put("description", "NovbeTime wallet top-up #" + request.id());
        payload.put("success_redirect_url", correlatedUrl(successUrl(), request.id()));
        payload.put("error_redirect_url", correlatedUrl(errorUrl(), request.id()));
        payload.put("result_url", resultUrl());
        return client.postForm(apiBaseUrl() + "/payment-request", payload);
    }

    private String correlatedUrl(String url, long requestId) {
        return UriComponentsBuilder.fromUriString(url).replaceQueryParam("requestId", requestId).build().toUriString();
    }

    private Map<String, Object> decodeCallback(String data) {
        try {
            return objectMapper.readValue(Base64.getDecoder().decode(data), objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint məbləği boşdur.");
        }
        Object operation = callback.get("operation_code");
        if (operation != null && !"100".equals(String.valueOf(operation))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Epoint əməliyyat növü uyğun deyil.");
        }
        BigDecimal actualAmount;
        try {
            actualAmount = new BigDecimal(String.valueOf(suppliedAmount));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint məbləği düzgün deyil.", exception);
        }
        if (actualAmount.compareTo(request.getAmountAzn()) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Epoint məbləği sorğu ilə uyğun deyil.");
        }
        if (suppliedCurrency != null && !request.getCurrency().equalsIgnoreCase(String.valueOf(suppliedCurrency))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Epoint məbləği sorğu ilə uyğun deyil.");
        }
    }

    private String providerReference(Map<String, Object> callback) {
        String reference = normalizeRequired(callback.get("transaction"), "Epoint transaction boşdur.");
        if (reference.length() > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint transaction düzgün deyil.");
        }
        return reference;
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
        if (!normalized.matches("(?:[0-9]+|wallet-[0-9]+-[0-9]+)")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint order id düzgün deyil.");
        }
        try {
            String id = normalized.startsWith("wallet-") ? normalized.split("-")[1] : normalized;
            long parsed = Long.parseLong(id);
            if (parsed <= 0) {
                throw new NumberFormatException("Non-positive ID");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Epoint order id düzgün deyil.");
        }
    }

    private void requireConfiguration() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Epoint konfiqurasiyası tamamlanmayıb.");
        }
    }

    private String apiBaseUrl() {
        return isBlank(properties.apiBaseUrl()) ? "https://epoint.az/api/1" : trimTrailingSlash(properties.apiBaseUrl());
    }

    private String successUrl() {
        return isBlank(properties.successUrl()) ? trimTrailingSlash(publicBaseUrl) + "/app/wallet?payment=success" : properties.successUrl();
    }

    private String errorUrl() {
        return isBlank(properties.errorUrl()) ? trimTrailingSlash(publicBaseUrl) + "/app/wallet?payment=failed" : properties.errorUrl();
    }

    private String resultUrl() {
        return isBlank(properties.resultUrl()) ? trimTrailingSlash(callbackBaseUrl) + CALLBACK_PATH : properties.resultUrl();
    }

    private String language() {
        return isBlank(properties.language()) ? "az" : properties.language().trim().toLowerCase(Locale.ROOT);
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
