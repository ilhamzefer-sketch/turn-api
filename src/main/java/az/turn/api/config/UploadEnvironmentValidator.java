package az.turn.api;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UploadEnvironmentValidator {
    private final String environment;
    private final UploadProperties properties;

    public UploadEnvironmentValidator(
            @Value("${app.env:local}") String environment,
            UploadProperties properties
    ) {
        this.environment = environment;
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        if (!isDeployedEnvironment()) {
            return;
        }
        if (!properties.antivirus().enabled()) {
            throw new IllegalStateException("Stage və prod mühitində antivirus aktiv olmalıdır.");
        }
        if (!properties.storageRoot().isAbsolute()) {
            throw new IllegalStateException("Stage və prod fayl storage yolu absolute olmalıdır.");
        }
    }

    private boolean isDeployedEnvironment() {
        return "stage".equalsIgnoreCase(environment) || "prod".equalsIgnoreCase(environment);
    }
}
