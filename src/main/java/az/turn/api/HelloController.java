package az.turn.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@Validated
public class HelloController {
    private static final String PAYMENT_SESSION_COOKIE = "payment_session_token";

    private final QueueService queueService;
    private final AuthService authService;
    private final boolean secureCookies;
    private final long refreshTokenDays;

    public HelloController(QueueService queueService, AuthService authService,
            @Value("${app.security.secure-cookies:false}") boolean secureCookies,
            @Value("${app.security.refresh-token-days:14}") long refreshTokenDays) {
        this.queueService = queueService;
        this.authService = authService;
        this.secureCookies = secureCookies;
        this.refreshTokenDays = refreshTokenDays;
    }

    @GetMapping("/")
    public String hello() {
        return "Hello World from Spring Boot!";
    }

    @PostMapping("/api/registrations")
    public RegistrationResponse createRegistration(@RequestBody RegistrationRequest request, HttpServletResponse response) {
        throw new ResponseStatusException(HttpStatus.GONE, "Birbaşa kart məlumatı qəbul edən köhnə qeydiyyat bağlanıb. Bank ödəniş sessiyasından istifadə edin.");
    }

    @PostMapping("/api/login")
    public RegistrationResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        RegistrationResponse registration = queueService.login(request);
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

    @PostMapping("/api/customers/register")
    public CustomerResponse registerCustomer(@Valid @RequestBody CustomerRegistrationRequest request, HttpServletResponse response) {
        CustomerResponse customer = queueService.registerCustomer(request);
        AuthTokens tokens = authService.issueTokens(new AuthenticatedUser(AuthUserType.CUSTOMER, customer.id(), customer.email()));
        writeRefreshCookie(response, tokens.refreshToken());
        return new CustomerResponse(customer.id(), customer.firstName(), customer.lastName(), customer.email(), customer.createdAt(), tokens.accessToken());
    }

    @PostMapping("/api/payments/registration-sessions")
    public PaymentSessionResponse createRegistrationPaymentSession(@Valid @RequestBody RegistrationPaymentSessionRequest request,
            HttpServletResponse response) {
        PaymentSessionResponse paymentSession = queueService.createRegistrationPaymentSession(request);
        writePaymentSessionCookie(response, paymentSession.sessionToken());
        return paymentSession;
    }

    @GetMapping("/api/payments/registration-sessions/{paymentSessionId}")
    public PaymentSessionResponse getRegistrationPaymentSession(@PathVariable @Positive long paymentSessionId,
            @RequestHeader(value = "X-Payment-Session-Token", required = false) String sessionToken,
            HttpServletRequest request) {
        return queueService.getPaymentSession(paymentSessionId, resolvePaymentSessionToken(request, sessionToken));
    }

    @PostMapping("/api/payments/registration-sessions/{paymentSessionId}/confirm")
    public PaymentConfirmationResponse confirmRegistrationPayment(@PathVariable @Positive long paymentSessionId,
            @RequestHeader(value = "X-Payment-Session-Token", required = false) String sessionToken,
            HttpServletRequest request, HttpServletResponse response) {
        PaymentConfirmationResponse paymentConfirmation = queueService.confirmRegistrationPayment(
                paymentSessionId, resolvePaymentSessionToken(request, sessionToken));
        RegistrationResponse registration = paymentConfirmation.registration();
        if (registration == null) {
            if (paymentConfirmation.payment().status() != PaymentStatus.PENDING) {
                clearPaymentSessionCookie(response);
            }
            return paymentConfirmation;
        }
        clearPaymentSessionCookie(response);
        AuthTokens tokens = authService.issueTokens(new AuthenticatedUser(AuthUserType.REGISTRATION, registration.id(), registration.email()));
        writeRefreshCookie(response, tokens.refreshToken());
        RegistrationResponse authenticatedRegistration = new RegistrationResponse(
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
        return new PaymentConfirmationResponse(paymentConfirmation.payment(), authenticatedRegistration);
    }

    @PostMapping("/api/payments/registration-sessions/{paymentSessionId}/cancel")
    public PaymentSessionResponse cancelRegistrationPayment(@PathVariable @Positive long paymentSessionId,
            @RequestHeader(value = "X-Payment-Session-Token", required = false) String sessionToken,
            HttpServletRequest request, HttpServletResponse response) {
        PaymentSessionResponse paymentSession = queueService.cancelRegistrationPayment(
                paymentSessionId, resolvePaymentSessionToken(request, sessionToken));
        clearPaymentSessionCookie(response);
        return paymentSession;
    }

    @PostMapping("/api/customers/login")
    public CustomerResponse loginCustomer(@Valid @RequestBody CustomerLoginRequest request, HttpServletResponse response) {
        CustomerResponse customer = queueService.loginCustomer(request);
        AuthTokens tokens = authService.issueTokens(new AuthenticatedUser(AuthUserType.CUSTOMER, customer.id(), customer.email()));
        writeRefreshCookie(response, tokens.refreshToken());
        return new CustomerResponse(customer.id(), customer.firstName(), customer.lastName(), customer.email(), customer.createdAt(), tokens.accessToken());
    }

    @PostMapping("/api/queue-managers/login")
    public QueueManagerLoginResponse loginQueueManager(@Valid @RequestBody QueueManagerLoginRequest request, HttpServletResponse response) {
        QueueManagerLoginResponse queueManager = queueService.loginQueueManager(request);
        AuthTokens tokens = authService.issueTokens(new AuthenticatedUser(AuthUserType.QUEUE_MANAGER, queueManager.queueManagerId(), queueManager.username()));
        writeRefreshCookie(response, tokens.refreshToken());
        return new QueueManagerLoginResponse(queueManager.queueManagerId(), queueManager.username(), queueManager.queue(), tokens.accessToken());
    }

    @PostMapping("/api/admin/login")
    public AdminLoginResponse loginAdmin(@Valid @RequestBody AdminLoginRequest request, HttpServletResponse response) {
        AdminLoginResponse admin = queueService.loginAdmin(request);
        AuthTokens tokens = authService.issueTokens(new AuthenticatedUser(AuthUserType.ADMIN, null, admin.username()));
        writeRefreshCookie(response, tokens.refreshToken());
        return new AdminLoginResponse(admin.username(), admin.role(), admin.message(), tokens.accessToken());
    }

    @GetMapping("/api/auth/csrf")
    public CsrfTokenResponse csrf(HttpServletRequest request) {
        String token = (String) request.getAttribute("csrfToken");
        return new CsrfTokenResponse(token);
    }

    @PostMapping("/api/auth/refresh")
    public AccessTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = CsrfCookieFilter.findCookieValue(request, "refresh_token");
        AuthTokens tokens = authService.refresh(refreshToken);
        writeRefreshCookie(response, tokens.refreshToken());
        return new AccessTokenResponse(tokens.accessToken());
    }

    @PostMapping("/api/auth/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.revoke(CsrfCookieFilter.findCookieValue(request, "refresh_token"));
        clearRefreshCookie(response);
    }

    @PostMapping("/api/queues")
    public QueueResponse createQueue(@Valid @RequestBody QueueCreateRequest request, Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication, AuthUserType.REGISTRATION);
        return queueService.createQueue(new QueueCreateRequest(
                user.userId(),
                request.address(),
                request.serviceName(),
                request.categories(),
                request.resetMode(),
                request.resetAt(),
                request.managerUsername(),
                request.managerPassword()
        ));
    }

    @GetMapping("/api/queues/public")
    public List<QueueResponse> getPublicQueues() {
        return queueService.getPublicQueues();
    }

    @PostMapping("/api/queues/scan")
    public QueueScanResponse scanQueue(@Valid @RequestBody QueueScanRequest request) {
        return queueService.scanQueue(request);
    }

    @PostMapping("/api/queues/join")
    public CustomerQueueJoinResponse joinQueue(@Valid @RequestBody CustomerQueueJoinRequest request, Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication, AuthUserType.CUSTOMER);
        return queueService.joinQueue(new CustomerQueueJoinRequest(user.userId(), request.queueId(), request.qrToken(), request.displayName()));
    }

    @PostMapping("/api/queues/{queueId}/next")
    public QueueDetailResponse advanceQueue(@PathVariable @Positive long queueId, Authentication authentication) {
        AuthenticatedUser user = requireAuthenticated(authentication);
        return queueService.advanceQueue(queueId, buildQueueAdvanceRequest(user));
    }

    @PostMapping("/api/queues/{queueId}/reset")
    public QueueDetailResponse resetQueue(@PathVariable @Positive long queueId, Authentication authentication) {
        AuthenticatedUser user = requireAuthenticated(authentication);
        return queueService.resetQueue(queueId, buildQueueResetRequest(user));
    }

    @GetMapping("/api/customers/{customerId}/history")
    public List<CustomerQueueHistoryItemResponse> getCustomerHistory(@PathVariable @Positive long customerId, Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication, AuthUserType.CUSTOMER);
        if (!user.userId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu tarixceye baxmaq icazeniz yoxdur.");
        }
        return queueService.getCustomerHistory(customerId);
    }

    @PostMapping("/api/customer-queue-entries/{entryId}/rename")
    public CustomerQueueEntryResponse renameCustomerQueueEntry(@PathVariable @Positive long entryId, @Valid @RequestBody CustomerQueueRenameRequest request, Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication, AuthUserType.CUSTOMER);
        return queueService.renameCustomerQueueEntry(entryId, new CustomerQueueRenameRequest(user.userId(), request.displayName()));
    }

    @PostMapping("/api/customer-queue-entries/{entryId}/rating")
    public CustomerQueueEntryResponse rateCustomerQueueEntry(@PathVariable @Positive long entryId, @Valid @RequestBody CustomerQueueRatingRequest request, Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication, AuthUserType.CUSTOMER);
        return queueService.rateCustomerQueueEntry(entryId, new CustomerQueueRatingRequest(user.userId(), request.rating(), request.note()));
    }

    @GetMapping("/api/registrations/{registrationId}/queues")
    public List<QueueResponse> getQueues(@PathVariable @Positive long registrationId, Authentication authentication) {
        AuthenticatedUser user = requireUser(authentication, AuthUserType.REGISTRATION);
        if (!user.userId().equals(registrationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu novbelere baxmaq icazeniz yoxdur.");
        }
        return queueService.getQueues(registrationId);
    }

    @GetMapping("/api/queues/{queueId}")
    public QueueDetailResponse getQueueDetail(
            @PathVariable @Positive long queueId,
            Authentication authentication
    ) {
        AuthenticatedUser user = requireAuthenticated(authentication);
        if (user.isRegistration()) {
            return queueService.getQueueDetail(queueId, user.userId(), null);
        }
        if (user.isQueueManager()) {
            return queueService.getQueueDetail(queueId, null, user.userId());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu novbe detalina baxmaq icazeniz yoxdur.");
    }

    @GetMapping("/api/admin/dashboard")
    public AdminDashboardResponse getAdminDashboard(
            @RequestParam(required = false) @Size(max = 150) String search,
            @RequestParam(required = false) @Pattern(regexp = "(?i)ALL|FERDI|KORPORATIV") String registrationType,
            @RequestParam(required = false) @Pattern(regexp = "(?i)ALL|PAID|UNPAID") String paymentStatus,
            @RequestParam(required = false) @Pattern(regexp = "\\d{4}-\\d{2}") String month,
            Authentication authentication
    ) {
        requireUser(authentication, AuthUserType.ADMIN);
        return queueService.getAdminDashboard(search, registrationType, paymentStatus, month);
    }

    private AuthenticatedUser requireAuthenticated(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Daxil olun.");
        }
        return user;
    }

    private AuthenticatedUser requireUser(Authentication authentication, AuthUserType userType) {
        AuthenticatedUser user = requireAuthenticated(authentication);
        if (user.userType() != userType) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu emeliyyat ucun icazeniz yoxdur.");
        }
        return user;
    }

    private QueueAdvanceRequest buildQueueAdvanceRequest(AuthenticatedUser user) {
        if (user.isRegistration()) {
            return new QueueAdvanceRequest(user.userId(), null);
        }
        if (user.isQueueManager()) {
            return new QueueAdvanceRequest(null, user.userId());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu emeliyyat ucun icazeniz yoxdur.");
    }

    private QueueResetRequest buildQueueResetRequest(AuthenticatedUser user) {
        if (user.isRegistration()) {
            return new QueueResetRequest(user.userId(), null);
        }
        if (user.isQueueManager()) {
            return new QueueResetRequest(null, user.userId());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu emeliyyat ucun icazeniz yoxdur.");
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

    private String resolvePaymentSessionToken(HttpServletRequest request, String headerToken) {
        String token = headerToken == null || headerToken.isBlank()
                ? CsrfCookieFilter.findCookieValue(request, PAYMENT_SESSION_COOKIE)
                : headerToken;
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Ödəniş sessiyası tokeni tapılmadı.");
        }
        return token;
    }

    private void writePaymentSessionCookie(HttpServletResponse response, String sessionToken) {
        ResponseCookie cookie = ResponseCookie.from(PAYMENT_SESSION_COOKIE, sessionToken)
                .httpOnly(true)
                .secure(secureCookies)
                .path("/api/payments/registration-sessions")
                .sameSite("Lax")
                .maxAge(30 * 60)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearPaymentSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(PAYMENT_SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookies)
                .path("/api/payments/registration-sessions")
                .sameSite("Lax")
                .maxAge(0)
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
