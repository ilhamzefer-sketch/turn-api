package az.turn.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class EpointSandboxConfigurer {
    private static final Logger log = LoggerFactory.getLogger(EpointSandboxConfigurer.class);

    private final EpointProperties properties;
    private final ObjectMapper objectMapper;

    public EpointSandboxConfigurer(EpointProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void configureCallback() {
        if (!properties.sandboxEnabled() || isBlank(properties.apiBaseUrl())
                || isBlank(properties.publicKey()) || isBlank(properties.resultUrl())) {
            return;
        }

        try {
            String sandboxUrl = trimTrailingSlash(properties.apiBaseUrl()).replaceFirst("/api/1$", "");
            byte[] body = objectMapper.writeValueAsBytes(Map.of(
                    "name", "NovbeTime",
                    "tin", "1201088522",
                    "contact_phone", "051-832-03-16",
                    "result_url", properties.resultUrl()
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sandboxUrl + "/_sandbox/merchants/" + properties.publicKey()))
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<Void> response = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("Sandbox returned HTTP " + response.statusCode());
            }
            log.info("Epoint sandbox callback configured.");
        } catch (Exception exception) {
            log.warn("Epoint sandbox callback could not be configured.", exception);
        }
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
