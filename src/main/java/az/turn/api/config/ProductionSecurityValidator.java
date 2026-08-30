package az.turn.api;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
public class ProductionSecurityValidator {
    private static final String DEFAULT_ADMIN_PASSWORD_HASH = "$2y$10$3Ehrpe5CUmnRa79/msUu3O2mQ.qwbdj.e/zefTCP8wfrqhxwHM/LG";
    private final String environment;
    private final String jwtSecret;
    private final List<String> allowedOrigins;
    private final String adminUsername;
    private final String adminPasswordHash;
    private final boolean legacyApiEnabled;
    private final boolean paymentReconciliationEnabled;
    private final String rateLimitStore;
    private final boolean redisSslEnabled;
    private final boolean secureCookies;

    public ProductionSecurityValidator(@Value("${app.env:local}") String environment,
            @Value("${app.security.jwt-secret:}") String jwtSecret,
            @Value("${app.security.allowed-origins:http://localhost:5275,http://127.0.0.1:5275,http://localhost:5173,http://127.0.0.1:5173}") List<String> allowedOrigins,
            @Value("${app.admin.username:admin}") String adminUsername,
            @Value("${app.admin.password-hash:}") String adminPasswordHash,
            @Value("${app.legacy-api.enabled:false}") boolean legacyApiEnabled,
            @Value("${app.payment.reconciliation-enabled:false}") boolean paymentReconciliationEnabled,
            @Value("${app.security.rate-limit.store:memory}") String rateLimitStore,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean redisSslEnabled,
            @Value("${app.security.secure-cookies:false}") boolean secureCookies) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.allowedOrigins = allowedOrigins;
        this.adminUsername = adminUsername;
        this.adminPasswordHash = adminPasswordHash;
        this.legacyApiEnabled = legacyApiEnabled;
        this.paymentReconciliationEnabled = paymentReconciliationEnabled;
        this.rateLimitStore = rateLimitStore;
        this.redisSslEnabled = redisSslEnabled;
        this.secureCookies = secureCookies;
    }

    @PostConstruct
    void validate() {
        if (!"prod".equalsIgnoreCase(environment)) return;
        require(jwtSecret != null && jwtSecret.length() >= 32 && !jwtSecret.contains("default-secret") && !jwtSecret.contains("replace-with"),
                "APP_JWT_SECRET güclü və unikal olmalıdır.");
        require(!legacyApiEnabled, "Prod mühitində legacy bank API-ləri aktiv edilə bilməz.");
        require(!paymentReconciliationEnabled, "Bank kartı aktivləşənədək payment reconciliation bağlı qalmalıdır.");
        require(allowedOrigins.stream().allMatch(this::isHttps), "Prod CORS origin-ləri yalnız HTTPS olmalıdır.");
        require(!"admin".equalsIgnoreCase(adminUsername)
                        && notBlank(adminPasswordHash)
                        && !DEFAULT_ADMIN_PASSWORD_HASH.equals(adminPasswordHash),
                "Prod admin credentials dəyişdirilməlidir.");
        require(adminPasswordHash.matches("^\\$2[aby]\\$.+"), "Prod admin şifrəsi BCrypt hash olmalıdır.");
        require("redis".equalsIgnoreCase(rateLimitStore), "Prod rate limit Redis istifadə etməlidir.");
        require(redisSslEnabled, "Prod Redis bağlantısında TLS aktiv olmalıdır.");
        require(secureCookies, "Prod refresh və CSRF cookie-ləri Secure olmalıdır.");
    }

    private boolean isHttps(String value) {
        try { return value != null && "https".equalsIgnoreCase(URI.create(value.trim()).getScheme()); }
        catch (IllegalArgumentException exception) { return false; }
    }

    private boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
