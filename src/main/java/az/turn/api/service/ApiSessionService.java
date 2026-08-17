package az.turn.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ApiSessionService {
    private static final String PAYMENT_SESSION_COOKIE = "payment_session_token";

    private final AuthService authService;
    private final UserMapper userMapper;
    private final SessionMetadataService sessionMetadataService;
    private final boolean secureCookies;
    private final long refreshTokenDays;

    public ApiSessionService(
            AuthService authService,
            UserMapper userMapper,
            SessionMetadataService sessionMetadataService,
            @Value("${app.security.secure-cookies:false}") boolean secureCookies,
            @Value("${app.security.refresh-token-days:14}") long refreshTokenDays
    ) {
        this.authService = authService;
        this.userMapper = userMapper;
        this.sessionMetadataService = sessionMetadataService;
        this.secureCookies = secureCookies;
        this.refreshTokenDays = refreshTokenDays;
    }

    public UserResponseDto authenticateUser(
            UserEntity user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthTokens tokens = authService.issueTokens(
                new AuthenticatedUser(AuthUserType.USER, user.getId(), user.getNormalizedPhone()),
                sessionMetadataService.from(request)
        );
        writeRefreshCookie(response, tokens.refreshToken());
        return userMapper.toDto(user, tokens.accessToken());
    }

    public RegistrationResponse authenticateRegistration(RegistrationResponse registration, HttpServletResponse response) {
        AuthTokens tokens = authService.issueTokens(new AuthenticatedUser(AuthUserType.REGISTRATION, registration.id(), registration.email()));
        writeRefreshCookie(response, tokens.refreshToken());
        return new RegistrationResponse(
                registration.id(),
                registration.firstName(),
                registration.lastName(),
                registration.email(),
                registration.paid(),
                registration.paymentReference(),
                registration.registrationType(),
                registration.status(),
                registration.createdAt(),
                tokens.accessToken()
        );
    }

    public CustomerResponse authenticateCustomer(CustomerResponse customer, HttpServletResponse response) {
        AuthTokens tokens = authService.issueTokens(new AuthenticatedUser(AuthUserType.CUSTOMER, customer.id(), customer.email()));
        writeRefreshCookie(response, tokens.refreshToken());
        return new CustomerResponse(
                customer.id(),
                customer.firstName(),
                customer.lastName(),
                customer.email(),
                customer.createdAt(),
                tokens.accessToken()
        );
    }

    public QueueManagerLoginResponse authenticateQueueManager(QueueManagerLoginResponse queueManager, HttpServletResponse response) {
        AuthTokens tokens = authService.issueTokens(new AuthenticatedUser(AuthUserType.QUEUE_MANAGER, queueManager.queueManagerId(), queueManager.username()));
        writeRefreshCookie(response, tokens.refreshToken());
        return new QueueManagerLoginResponse(
                queueManager.queueManagerId(),
                queueManager.username(),
                queueManager.queue(),
                tokens.accessToken()
        );
    }

    public AdminLoginResponse authenticateAdmin(AdminLoginResponse admin, HttpServletResponse response) {
        AuthTokens tokens = authService.issueTokens(new AuthenticatedUser(AuthUserType.ADMIN, null, admin.username()));
        writeRefreshCookie(response, tokens.refreshToken());
        return new AdminLoginResponse(admin.username(), admin.role(), admin.message(), tokens.accessToken());
    }

    public AccessTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = CsrfCookieFilter.findCookieValue(request, "refresh_token");
        AuthTokens tokens = authService.refresh(refreshToken, sessionMetadataService.from(request));
        writeRefreshCookie(response, tokens.refreshToken());
        return new AccessTokenResponse(tokens.accessToken());
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.revoke(CsrfCookieFilter.findCookieValue(request, "refresh_token"));
        clearRefreshCookie(response);
    }

    public void writePaymentSessionCookie(HttpServletResponse response, String sessionToken) {
        ResponseCookie cookie = ResponseCookie.from(PAYMENT_SESSION_COOKIE, sessionToken)
                .httpOnly(true)
                .secure(secureCookies)
                .path("/api")
                .sameSite("Lax")
                .maxAge(30 * 60)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearPaymentSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(PAYMENT_SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookies)
                .path("/api")
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String resolvePaymentSessionToken(HttpServletRequest request, String headerToken) {
        String token = headerToken == null || headerToken.isBlank()
                ? CsrfCookieFilter.findCookieValue(request, PAYMENT_SESSION_COOKIE)
                : headerToken;
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ã–dÉ™niÅŸ sessiyasÄ± tokeni tapÄ±lmadÄ±.");
        }
        return token;
    }

    private void writeRefreshCookie(HttpServletResponse response, String refreshToken) {
        clearCookieAtPath(response, "/");
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(secureCookies)
                .path("/api/auth")
                .sameSite("Lax")
                .maxAge(refreshTokenDays * 24 * 60 * 60)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        clearCookieAtPath(response, "/");
        clearCookieAtPath(response, "/api/auth");
    }

    private void clearCookieAtPath(HttpServletResponse response, String path) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(secureCookies)
                .path(path)
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
