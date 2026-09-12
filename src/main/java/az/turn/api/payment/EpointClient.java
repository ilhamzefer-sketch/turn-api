package az.turn.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class EpointClient {
    private final EpointProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public EpointClient(EpointProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Map<String, Object> postForm(String endpoint, Map<String, Object> payload) throws IOException {
        String data = Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(payload));
        String signature = EpointSignature.sign(data, properties.privateKey());
        String body = "data=" + URLEncoder.encode(data, StandardCharsets.UTF_8)
                + "&signature=" + URLEncoder.encode(signature, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(properties.requestTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        CompletableFuture<HttpResponse<String>> pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            HttpResponse<String> response = pending.get(properties.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length() > 65536) {
                throw new IOException("Invalid Epoint response: HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(),
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Epoint request interrupted.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("Epoint request did not complete.", exception);
        } finally {
            pending.cancel(true);
        }
    }
}
