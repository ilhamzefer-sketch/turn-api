package az.turn.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "http")
public class HttpSmsSender implements SmsSender {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String bearerToken;
    private final String senderName;
    private final Duration requestTimeout;

    public HttpSmsSender(
            ObjectMapper objectMapper,
            @Value("${app.sms.endpoint}") String endpoint,
            @Value("${app.sms.bearer-token:}") String bearerToken,
            @Value("${app.sms.sender-name:NovbeTime}") String senderName,
            @Value("${app.sms.connect-timeout-seconds:5}") long connectTimeoutSeconds,
            @Value("${app.sms.request-timeout-seconds:10}") long requestTimeoutSeconds
    ) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("SMS endpoint must be configured for the HTTP provider");
        }
        if (connectTimeoutSeconds <= 0 || requestTimeoutSeconds <= 0) {
            throw new IllegalStateException("SMS timeouts must be positive");
        }
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(endpoint);
        this.bearerToken = bearerToken;
        this.senderName = senderName;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    @Override
    public void send(String phone, String message) {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(phone, message)));
        if (bearerToken != null && !bearerToken.isBlank()) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        try {
            HttpResponse<Void> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SmsDeliveryException("SMS gateway returned status " + response.statusCode());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SmsDeliveryException("SMS delivery was interrupted", exception);
        } catch (IOException exception) {
            throw new SmsDeliveryException("SMS gateway is unavailable", exception);
        }
    }

    private String body(String phone, String message) {
        try {
            return objectMapper.writeValueAsString(new SmsGatewayRequestDto(phone, message, senderName));
        } catch (JsonProcessingException exception) {
            throw new SmsDeliveryException("SMS request could not be serialized", exception);
        }
    }
}
