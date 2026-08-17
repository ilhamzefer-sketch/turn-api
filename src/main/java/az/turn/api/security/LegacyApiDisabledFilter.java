package az.turn.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnProperty(name = "app.legacy-api.enabled", havingValue = "false", matchIfMissing = true)
public class LegacyApiDisabledFilter extends OncePerRequestFilter {
    private static final List<String> PREFIXES = List.of(
            "/api/customers",
            "/api/queue-managers",
            "/api/registrations",
            "/api/queues",
            "/api/payments/registration-sessions"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("/api/login".equals(path) || "/api/admin/dashboard".equals(path)
                || PREFIXES.stream().anyMatch(path::startsWith)) {
            response.setStatus(HttpServletResponse.SC_GONE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\":\"Bu köhnə API bağlanıb. Telefon əsaslı yeni API-dən istifadə edin.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
