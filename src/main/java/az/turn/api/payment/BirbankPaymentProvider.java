package az.turn.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.math.BigDecimal;
import java.util.Base64;

@Component
public class BirbankPaymentProvider implements PaymentProvider {

    private static final Logger logger = LoggerFactory.getLogger(BirbankPaymentProvider.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String callbackBaseUrl;
    private final String username;
    private final String password;

    public BirbankPaymentProvider(
            ObjectMapper objectMapper,
            @Value("${app.payment.birbank.base-url:}") String baseUrl,
            @Value("${app.payment.callback-base-url:http://127.0.0.1:5173}") String callbackBaseUrl,
            @Value("${app.payment.birbank.username:}") String username,
            @Value("${app.payment.birbank.password:}") String password
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.callbackBaseUrl = trimTrailingSlash(callbackBaseUrl);
        this.username = username;
        this.password = password;
    }

    @Override
    public String providerName() {
        return "birbank";
    }

    @Override
    public void initialize(PaymentSessionEntity session) {
        validateConfiguration();
        try {
            String requestBody = objectMapper.writeValueAsString(new CreateOrderEnvelope(
                    new CreateOrderRequest(
                            "Order_SMS",
                            String.valueOf(session.getAmount()),
                            session.getCurrency(),
                            "az",
                            "E-Novbe",
                            session.getRegistrationType() == RegistrationType.KORPORATIV
                                    ? "Korporativ qeydiyyat"
                                    : "Ferdi qeydiyyat",
                            callbackBaseUrl + "/payments/" + session.getId() + "?token=" + encodePath(session.getSessionToken())
                    )
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/order"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", basicAuthHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Kapital order creation finished: httpStatus={}, paymentSessionId={}", response.statusCode(), session.getId());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kapital sifarişi yaratmaq mümkün olmadı.");
            }

            CreateOrderResponseEnvelope createOrderResponse = objectMapper.readValue(response.body(), CreateOrderResponseEnvelope.class);
            if (createOrderResponse.order() == null || createOrderResponse.order().id() == null
                    || isBlank(createOrderResponse.order().password()) || isBlank(createOrderResponse.order().hppUrl())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kapital cavabı natamam gəldi.");
            }

            session.setExternalOrderId(String.valueOf(createOrderResponse.order().id()));
            session.setExternalOrderPassword(createOrderResponse.order().password());
            session.setExternalHppUrl(createOrderResponse.order().hppUrl());
            session.setPaymentReference("KAPITAL-ORDER-" + createOrderResponse.order().id());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kapital sifariş yaratma sorğusu dayandırıldı.");
        } catch (IOException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kapital sifariş yaratmaq xətası baş verdi.");
        }
    }

    @Override
    public PaymentStatus confirm(PaymentSessionEntity session) {
        validateConfiguration();
        if (isBlank(session.getExternalOrderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kapital sifaris ID tapilmadi.");
        }

        try {
            String query = "?tranDetailLevel=2&tokenDetailLevel=2&orderDetailLevel=2";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/order/" + encodePath(session.getExternalOrderId()) + query))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", basicAuthHeader())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Kapital order status received: httpStatus={}, paymentSessionId={}, externalOrderId={}",
                    response.statusCode(), session.getId(), session.getExternalOrderId());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kapital sifariş statusu oxunmadı.");
            }

            OrderDetailsEnvelope orderDetails = objectMapper.readValue(response.body(), OrderDetailsEnvelope.class);
            if (orderDetails.order() == null || isBlank(orderDetails.order().status())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kapital sifariş statusu boş gəldi.");
            }

            String status = orderDetails.order().status();
            session.setPaymentReference("KAPITAL-ORDER-" + orderDetails.order().id());

            if ("FullyPaid".equalsIgnoreCase(status) || "Paid".equalsIgnoreCase(status)) {
                validateCompletedOrder(session, orderDetails.order());
                return PaymentStatus.COMPLETED;
            }
            if ("Preparing".equalsIgnoreCase(status) || "Created".equalsIgnoreCase(status)
                    || "Processing".equalsIgnoreCase(status) || "Authorized".equalsIgnoreCase(status)) {
                return PaymentStatus.PENDING;
            }
            if ("Cancelled".equalsIgnoreCase(status) || "Canceled".equalsIgnoreCase(status)
                    || "Declined".equalsIgnoreCase(status) || "Rejected".equalsIgnoreCase(status)
                    || "Expired".equalsIgnoreCase(status)) {
                return PaymentStatus.CANCELLED;
            }
            logger.warn("Unknown Kapital payment status: status={}, paymentSessionId={}", status, session.getId());
            return PaymentStatus.FAILED;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kapital status sorğusu dayandırıldı.");
        } catch (IOException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Kapital status yoxlanışı xətası baş verdi.");
        }
    }

    private void validateCompletedOrder(PaymentSessionEntity session, OrderDetails order) {
        boolean orderMatches = order.id() != null && String.valueOf(order.id()).equals(session.getExternalOrderId());
        boolean currencyMatches = session.getCurrency().equalsIgnoreCase(order.currency());
        BigDecimal expectedAmount = BigDecimal.valueOf(session.getAmount());
        boolean orderAmountMatches = order.amount() != null && order.amount().compareTo(expectedAmount) == 0;
        BigDecimal chargedAmount = order.clearedChargeAmount() != null ? order.clearedChargeAmount() : order.authorizedChargeAmount();
        boolean chargeMatches = chargedAmount != null && chargedAmount.compareTo(expectedAmount) >= 0;
        if (!orderMatches || !currencyMatches || !orderAmountMatches || !chargeMatches) {
            logger.error("Kapital completed order verification failed: paymentSessionId={}, externalOrderId={}",
                    session.getId(), session.getExternalOrderId());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Bank ödəniş məlumatları sifarişlə uyğun gəlmir.");
        }
    }

    private void validateConfiguration() {
        if (isBlank(baseUrl) || isBlank(callbackBaseUrl) || isBlank(username) || isBlank(password)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Bank ödəniş konfiqurasiyası tamamlanmayıb.");
        }
    }

    private String basicAuthHeader() {
        String raw = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CreateOrderEnvelope(CreateOrderRequest order) {
    }

    private record CreateOrderRequest(
            String typeRid,
            String amount,
            String currency,
            String language,
            String title,
            String description,
            @JsonProperty("hppRedirectUrl") String hppRedirectUrl
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreateOrderResponseEnvelope(CreateOrderResponse order) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreateOrderResponse(
            Long id,
            String hppUrl,
            String password,
            String status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderDetailsEnvelope(OrderDetails order) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderDetails(
            Long id,
            String status,
            BigDecimal amount,
            String currency,
            BigDecimal authorizedChargeAmount,
            BigDecimal clearedChargeAmount
    ) {
    }
}
