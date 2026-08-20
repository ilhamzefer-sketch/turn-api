package az.turn.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Component
public class AbbPaymentProvider implements PaymentProvider {
    private static final Logger logger = LoggerFactory.getLogger(AbbPaymentProvider.class);

    private final ObjectMapper objectMapper;
    private final AbbPaymentXmlFactory paymentXmlFactory;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String username;
    private final String password;
    private final String debitAccount;
    private final String recipientName;
    private final String recipientAccount;
    private final String recipientTaxId;
    private final String recipientBankCode;
    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    public AbbPaymentProvider(
            ObjectMapper objectMapper,
            AbbPaymentXmlFactory paymentXmlFactory,
            @Value("${app.payment.abb.base-url:}") String baseUrl,
            @Value("${app.payment.abb.username:}") String username,
            @Value("${app.payment.abb.password:}") String password,
            @Value("${app.payment.abb.debit-account:}") String debitAccount,
            @Value("${app.payment.abb.recipient-name:}") String recipientName,
            @Value("${app.payment.abb.recipient-account:}") String recipientAccount,
            @Value("${app.payment.abb.recipient-tax-id:}") String recipientTaxId,
            @Value("${app.payment.abb.recipient-bank-code:}") String recipientBankCode
    ) {
        this.objectMapper = objectMapper;
        this.paymentXmlFactory = paymentXmlFactory;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.username = username;
        this.password = password;
        this.debitAccount = debitAccount;
        this.recipientName = recipientName;
        this.recipientAccount = recipientAccount;
        this.recipientTaxId = recipientTaxId;
        this.recipientBankCode = recipientBankCode;
    }

    @Override
    public String providerName() {
        return "abb";
    }

    @Override
    public void initialize(PaymentSessionEntity session) {
        validateConfiguration();
        String externalReference = "ENOVBE-" + session.getId();
        String xml = paymentXmlFactory.create(
                externalReference,
                session.getAmount(),
                debitAccount,
                recipientName,
                recipientAccount,
                recipientTaxId,
                recipientBankCode
        );
        AbbPaymentFileRequestDto payload = new AbbPaymentFileRequestDto(
                Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8)),
                externalReference
        );
        AbbPaymentFileResponseDto response = post("/payments/", payload, AbbPaymentFileResponseDto.class);
        if (response.data() == null || isBlank(response.data().batchNumber())) {
            throw bankError("ABB ödəniş faylı üçün batch nömrəsi qaytarmadı.");
        }
        session.setExternalOrderId(response.data().batchNumber());
        session.setPaymentReference(externalReference);
        logger.info("ABB payment file accepted: paymentSessionId={}, batchNumber={}",
                session.getId(), response.data().batchNumber());
    }

    @Override
    public PaymentStatus confirm(PaymentSessionEntity session) {
        validateConfiguration();
        if (isBlank(session.getPaymentReference()) || isBlank(session.getExternalOrderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ABB ödəniş identifikatoru tapılmadı.");
        }
        String path = "/payments/file-status?external-reference=" + encode(session.getPaymentReference());
        AbbFileStatusResponseDto response = get(path, AbbFileStatusResponseDto.class);
        if (!session.getPaymentReference().equals(response.externalReference())
                || !session.getExternalOrderId().equals(response.batchNumber())
                || response.status() == null || isBlank(response.status().status())) {
            throw bankError("ABB ödəniş statusu sessiya ilə uyğun gəlmir.");
        }
        return mapStatus(response.status().status(), session.getId());
    }

    private PaymentStatus mapStatus(String value, Long paymentSessionId) {
        if ("COMPLETED".equalsIgnoreCase(value)) return PaymentStatus.COMPLETED;
        if ("IN_PROGRESS".equalsIgnoreCase(value)) return PaymentStatus.PENDING;
        if ("FAILURE".equalsIgnoreCase(value) || "ERROR".equalsIgnoreCase(value)
                || "PARTIAL".equalsIgnoreCase(value)) return PaymentStatus.FAILED;
        logger.warn("Unknown ABB file status: status={}, paymentSessionId={}", value, paymentSessionId);
        return PaymentStatus.PENDING;
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            return send(request, responseType);
        } catch (IOException exception) {
            throw bankError("ABB ödəniş sorğusu hazırlana bilmədi.");
        }
    }

    private <T> T get(String path, Class<T> responseType) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token())
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(request, responseType);
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("ABB request failed: path={}, httpStatus={}", request.uri().getPath(), response.statusCode());
                throw bankError("ABB test API sorğusu uğursuz oldu.");
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw bankError("ABB sorğusu dayandırıldı.");
        } catch (IOException | IllegalArgumentException exception) {
            throw bankError("ABB test API cavabı oxuna bilmədi.");
        }
    }

    private synchronized String token() {
        if (!isBlank(accessToken) && Instant.now().isBefore(accessTokenExpiresAt)) return accessToken;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/payments/auth/token"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                            new AbbAuthRequestDto(username, password))))
                    .build();
            AbbAuthResponseDto response = send(request, AbbAuthResponseDto.class);
            if (isBlank(response.accessToken())) throw bankError("ABB giriş tokeni qaytarmadı.");
            accessToken = response.accessToken();
            long lifetime = response.expiresIn() > 120 ? response.expiresIn() - 60 : 60;
            accessTokenExpiresAt = Instant.now().plusSeconds(lifetime);
            return accessToken;
        } catch (IOException exception) {
            throw bankError("ABB giriş sorğusu hazırlana bilmədi.");
        }
    }

    private void validateConfiguration() {
        if (isBlank(baseUrl) || isBlank(username) || isBlank(password) || isBlank(debitAccount)
                || isBlank(recipientName) || isBlank(recipientAccount) || isBlank(recipientTaxId)
                || isBlank(recipientBankCode)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "ABB test ödəniş konfiqurasiyası tamamlanmayıb.");
        }
    }

    private ResponseStatusException bankError(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
