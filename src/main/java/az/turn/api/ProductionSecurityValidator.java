package az.turn.api;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
public class ProductionSecurityValidator {
    private final String environment;
    private final String jwtSecret;
    private final String callbackBaseUrl;
    private final String bankBaseUrl;
    private final String bankUsername;
    private final String bankPassword;
    private final List<String> allowedOrigins;
    private final String adminUsername;
    private final String adminPasswordHash;
    private final String paymentMode;
    private final String paymentProvider;
    private final String rateLimitStore;
    private final boolean redisSslEnabled;

    public ProductionSecurityValidator(@Value("${app.env:local}") String environment,
            @Value("${app.security.jwt-secret:}") String jwtSecret,
            @Value("${app.payment.callback-base-url:}") String callbackBaseUrl,
            @Value("${app.payment.birbank.base-url:}") String bankBaseUrl,
            @Value("${app.payment.birbank.username:}") String bankUsername,
            @Value("${app.payment.birbank.password:}") String bankPassword,
            @Value("${app.security.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") List<String> allowedOrigins,
            @Value("${app.admin.username:admin}") String adminUsername,
            @Value("${app.admin.password-hash:}") String adminPasswordHash,
            @Value("${app.payment.mode:test}") String paymentMode,
            @Value("${app.payment.provider:birbank}") String paymentProvider,
            @Value("${app.security.rate-limit.store:memory}") String rateLimitStore,
            @Value("${spring.data.redis.ssl.enabled:false}") boolean redisSslEnabled) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.callbackBaseUrl = callbackBaseUrl;
        this.bankBaseUrl = bankBaseUrl;
        this.bankUsername = bankUsername;
        this.bankPassword = bankPassword;
        this.allowedOrigins = allowedOrigins;
        this.adminUsername = adminUsername;
        this.adminPasswordHash = adminPasswordHash;
        this.paymentMode = paymentMode;
        this.paymentProvider = paymentProvider;
        this.rateLimitStore = rateLimitStore;
        this.redisSslEnabled = redisSslEnabled;
    }

    @PostConstruct
    void validate() {
        if (!"prod".equalsIgnoreCase(environment)) return;
        require(jwtSecret != null && jwtSecret.length() >= 32 && !jwtSecret.contains("default-secret") && !jwtSecret.contains("replace-with"),
                "APP_JWT_SECRET güclü və unikal olmalıdır.");
        require(isHttps(callbackBaseUrl), "Prod callback URL HTTPS olmalıdır.");
        require(isHttps(bankBaseUrl), "Prod bank API URL HTTPS olmalıdır.");
        require(!bankBaseUrl.toLowerCase().contains("txpgtst") && !bankBaseUrl.toLowerCase().contains("pre-"),
                "Prod profilində Kapital test API URL-i istifadə edilə bilməz.");
        require(notPlaceholder(bankUsername) && notPlaceholder(bankPassword), "Prod bank credentials mütləqdir.");
        require(allowedOrigins.stream().allMatch(this::isHttps), "Prod CORS origin-ləri yalnız HTTPS olmalıdır.");
        require(!"admin".equalsIgnoreCase(adminUsername) && notBlank(adminPasswordHash), "Prod admin credentials dəyişdirilməlidir.");
        require(adminPasswordHash.matches("^\\$2[aby]\\$.+"), "Prod admin şifrəsi BCrypt hash olmalıdır.");
        require("live".equalsIgnoreCase(paymentMode) && "birbank".equalsIgnoreCase(paymentProvider),
                "Prod ödəniş rejimi live və provider birbank olmalıdır.");
        require("redis".equalsIgnoreCase(rateLimitStore), "Prod rate limit Redis istifadə etməlidir.");
        require(redisSslEnabled, "Prod Redis bağlantısında TLS aktiv olmalıdır.");
    }

    private boolean isHttps(String value) {
        try { return value != null && "https".equalsIgnoreCase(URI.create(value.trim()).getScheme()); }
        catch (IllegalArgumentException exception) { return false; }
    }

    private boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private boolean notPlaceholder(String value) { return notBlank(value) && !value.toLowerCase().contains("replace-with"); }
    private void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
