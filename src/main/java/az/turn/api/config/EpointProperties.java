package az.turn.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.epoint")
public record EpointProperties(
        String publicKey,
        String privateKey,
        String apiBaseUrl,
        String successUrl,
        String errorUrl,
        String resultUrl,
        String language,
        String currency,
        boolean sandboxEnabled
) {
}
