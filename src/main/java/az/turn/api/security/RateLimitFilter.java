package az.turn.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final RateLimitStore rateLimitStore;
    private final boolean enabled;
    private final boolean trustProxyHeaders;
    private final int globalLimit;
    private final int authLimit;
    private final int paymentLimit;
    private final int publicQueueLimit;

    public RateLimitFilter(ObjectMapper objectMapper, RateLimitStore rateLimitStore,
            @Value("${app.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.security.trust-proxy-headers:false}") boolean trustProxyHeaders,
            @Value("${app.security.rate-limit.global-per-minute:300}") int globalLimit,
            @Value("${app.security.rate-limit.auth-per-minute:10}") int authLimit,
            @Value("${app.security.rate-limit.payment-per-minute:30}") int paymentLimit,
            @Value("${app.security.rate-limit.public-queue-per-minute:60}") int publicQueueLimit) {
        this.objectMapper = objectMapper;
        this.rateLimitStore = rateLimitStore;
        this.enabled = enabled;
        this.trustProxyHeaders = trustProxyHeaders;
        this.globalLimit = positive(globalLimit, "global-per-minute");
        this.authLimit = positive(authLimit, "auth-per-minute");
        this.paymentLimit = positive(paymentLimit, "payment-per-minute");
        this.publicQueueLimit = positive(publicQueueLimit, "public-queue-per-minute");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = Instant.now().getEpochSecond();
        String client = resolveClientAddress(request);
        LimitRule rule = resolveRule(request.getRequestURI());
        RateLimitDecision globalDecision = rateLimitStore.consume("global:" + client, globalLimit, now);
        RateLimitDecision routeDecision = rateLimitStore.consume(rule.name() + ":" + client, rule.limit(), now);
        RateLimitDecision decision = globalDecision.allowed() ? routeDecision : globalDecision;

        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetEpochSeconds()));
        if (!decision.allowed()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", String.valueOf(Math.max(1, decision.resetEpochSeconds() - now)));
            objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(OffsetDateTime.now(), 429,
                    "Too Many Requests", "Çox sayda sorğu göndərildi. Bir qədər sonra yenidən cəhd edin.", request.getRequestURI()));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private LimitRule resolveRule(String path) {
        if (path.matches("/api/(login|admin/login|customers/(login|register)|queue-managers/login|auth/(login|register|refresh))")) return new LimitRule("auth", authLimit);
        if (path.startsWith("/api/payments/")) return new LimitRule("payment", paymentLimit);
        if (path.equals("/api/queues/scan") || path.equals("/api/queues/join")
                || path.equals("/api/queues/public") || path.startsWith("/api/public/")) {
            return new LimitRule("public-queue", publicQueueLimit);
        }
        return new LimitRule("route", globalLimit);
    }

    private String resolveClientAddress(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String candidate = forwarded.split(",", 2)[0].trim();
                if (candidate.matches("^[0-9a-fA-F:.]{3,45}$")) return candidate;
            }
        }
        return request.getRemoteAddr();
    }

    private int positive(int value, String property) {
        if (value < 1) throw new IllegalArgumentException("Rate limit " + property + " must be positive");
        return value;
    }
}
