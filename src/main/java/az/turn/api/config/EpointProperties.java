package az.turn.api;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

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
        boolean sandboxEnabled,
        Duration connectTimeout,
        Duration requestTimeout
) {
    public EpointProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
        if (connectTimeout.isNegative() || connectTimeout.isZero() || requestTimeout.isNegative()
                || requestTimeout.isZero() || requestTimeout.compareTo(Duration.ofSeconds(15)) > 0
                || connectTimeout.compareTo(requestTimeout) > 0) {
            throw new IllegalArgumentException("Epoint deadlines must be positive, connect <= request <= 15s.");
        }
    }
}
