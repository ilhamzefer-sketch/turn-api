package az.turn.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Component
public class EpointPaymentProvider implements PaymentProvider {
    private static final Logger log = LoggerFactory.getLogger(EpointPaymentProvider.class);

    private final EpointProperties properties;
    private final ObjectMapper objectMapper;
    private final String publicBaseUrl;
    private final String callbackBaseUrl;

    public EpointPaymentProvider(
            EpointProperties properties,
            ObjectMapper objectMapper,
            @Value("${app.public-base-url:https://novbetime.az}") String publicBaseUrl,
            @Value("${app.payment.callback-base-url:https://novbetime.az}") String callbackBaseUrl
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.publicBaseUrl = publicBaseUrl;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    @Override
    public String providerName() {
        return "epoint";
    }

    @Override
    public void initialize(PaymentSessionEntity session) {
        requireConfiguration();
        String orderId = orderIdFor(session);
        try {
            String redirectUrl = createCheckoutUrl(session, orderId);
            session.setExternalOrderId(orderId);
            session.setExternalOrderPassword(null);
            session.setExternalHppUrl(redirectUrl);
            session.setPaymentReference("EPOINT-" + orderId);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Epoint registration checkout request failed for payment session {}", session.getId(), exception);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Epoint checkout yaradila bilm?di.", exception);
        }
    }

    @Override
    public PaymentStatus confirm(PaymentSessionEntity session) {
        if (properties.sandboxEnabled()) {
            return "decline".equalsIgnoreCase(session.getSandboxOutcome()) ? PaymentStatus.FAILED : PaymentStatus.COMPLETED;
        }
        return PaymentStatus.PENDING;
    }

    private String createCheckoutUrl(PaymentSessionEntity session, String orderId) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("public_key", properties.publicKey());
        payload.put("amount", BigDecimal.valueOf(session.getAmount()).stripTrailingZeros().toPlainString());
        payload.put("currency", currency(session));
        payload.put("language", language());
        payload.put("order_id", orderId);
        payload.put("description", description(session));
        payload.put("success_redirect_url", successUrl(session));
        payload.put("error_redirect_url", errorUrl(session));
        payload.put("result_url", resultUrl());

        String encodedData = Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(payload));
        String signedData = EpointSignature.sign(encodedData, properties.privateKey());
        Map<String, Object> response = postForm(apiBaseUrl() + "/payment-request", encodedData, signedData);
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

    private String orderIdFor(PaymentSessionEntity session) {
        if (session.getId() == null || session.getId() <= 0) {
            throw new IllegalArgumentException("Payment session must be persisted.");
        }
        return "registration-" + session.getId() + "-" + Instant.now().toEpochMilli();
    }

    private String successUrl(PaymentSessionEntity session) {
        return trimTrailingSlash(publicBaseUrl) + "/payments/" + session.getId();
    }

    private String errorUrl(PaymentSessionEntity session) {
        return trimTrailingSlash(publicBaseUrl) + "/payments/" + session.getId() + "?payment=failed";
    }

    private String resultUrl() {
        if (!isBlank(properties.resultUrl())) {
            return properties.resultUrl();
        }
        return trimTrailingSlash(callbackBaseUrl) + "/api/payments/epoint/callback";
    }

    private String description(PaymentSessionEntity session) {
        if (session.getRegistrationType() == RegistrationType.KORPORATIV) {
            return "NovbeTime korporativ qeydiyyat #" + session.getId();
        }
        return "NovbeTime ferdi qeydiyyat #" + session.getId();
    }

    private String apiBaseUrl() {
        return isBlank(properties.apiBaseUrl()) ? "https://epoint.az/api/1" : trimTrailingSlash(properties.apiBaseUrl());
    }

    private String language() {
        return isBlank(properties.language()) ? "az" : properties.language().trim().toLowerCase(Locale.ROOT);
    }

    private String currency(PaymentSessionEntity session) {
        return isBlank(session.getCurrency()) ? currency() : session.getCurrency().trim().toUpperCase(Locale.ROOT);
    }

    private String currency() {
        return isBlank(properties.currency()) ? "AZN" : properties.currency().trim().toUpperCase(Locale.ROOT);
    }

    private void requireConfiguration() {
        if (isBlank(properties.publicKey()) || isBlank(properties.privateKey())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Epoint konfiqurasiyasi tamamlanmayib.");
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
