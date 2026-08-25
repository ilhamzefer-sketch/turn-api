package az.turn.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

@Component
public class CsrfCookieFilter extends OncePerRequestFilter {

    public static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name());
    private static final Set<String> ROTATION_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/login",
            "/api/customers/register",
            "/api/customers/login",
            "/api/queue-managers/login",
            "/api/admin/login",
            "/api/auth/logout"
    );
    private final SecureRandom secureRandom = new SecureRandom();
    private final boolean secureCookies;

    public CsrfCookieFilter(@Value("${app.security.secure-cookies:false}") boolean secureCookies) {
        this.secureCookies = secureCookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/auth/") || ROTATION_PATHS.contains(request.getRequestURI())) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        String token = ensureCookie(request, response);
        request.setAttribute("csrfToken", token);

        if (!SAFE_METHODS.contains(request.getMethod())) {
            String cookieToken = findCookieValue(request, CSRF_COOKIE_NAME);
            String headerToken = request.getHeader(CSRF_HEADER_NAME);
            if (cookieToken == null || headerToken == null || !cookieToken.equals(headerToken)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"message\":\"CSRF token sehvdir.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
        if (response.getStatus() < 400 && ROTATION_PATHS.contains(request.getRequestURI())) {
            writeToken(response, generateToken());
        }
    }

    private String ensureCookie(HttpServletRequest request, HttpServletResponse response) {
        String existing = findCookieValue(request, CSRF_COOKIE_NAME);
        if (existing != null) {
            return existing;
        }

        String token = generateToken();
        writeToken(response, token);
        return token;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void writeToken(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE_NAME, token)
                .httpOnly(false).secure(secureCookies).path("/").sameSite("Lax").build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.setHeader(CSRF_HEADER_NAME, token);
    }

    public static String findCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
