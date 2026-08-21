package az.turn.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AccountService accountService;
    private final UserAccountService userAccountService;
    private final ApiSessionService apiSessionService;

    public AuthController(
            AccountService accountService,
            UserAccountService userAccountService,
            ApiSessionService apiSessionService
    ) {
        this.accountService = accountService;
        this.userAccountService = userAccountService;
        this.apiSessionService = apiSessionService;
    }

    @PostMapping({"/api/auth/register", "/api/auth/registerRequest"})
    public UserResponseDto registerUser(
            @Valid @RequestBody UserRegistrationRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        return apiSessionService.authenticateUser(userAccountService.register(request), httpRequest, response);
    }

    @PostMapping({"/api/auth/login", "/api/auth/loginRequest"})
    public UserResponseDto loginUser(
            @Valid @RequestBody UserLoginRequestDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        return apiSessionService.authenticateUser(userAccountService.login(request), httpRequest, response);
    }

    @PostMapping("/api/login")
    public RegistrationResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return apiSessionService.authenticateRegistration(accountService.login(request), response);
    }

    @PostMapping("/api/customers/register")
    public CustomerResponse registerCustomer(@Valid @RequestBody CustomerRegistrationRequest request, HttpServletResponse response) {
        return apiSessionService.authenticateCustomer(accountService.registerCustomer(request), response);
    }

    @PostMapping("/api/customers/login")
    public CustomerResponse loginCustomer(@Valid @RequestBody CustomerLoginRequest request, HttpServletResponse response) {
        return apiSessionService.authenticateCustomer(accountService.loginCustomer(request), response);
    }

    @PostMapping("/api/queue-managers/login")
    public QueueManagerLoginResponse loginQueueManager(@Valid @RequestBody QueueManagerLoginRequest request, HttpServletResponse response) {
        return apiSessionService.authenticateQueueManager(accountService.loginQueueManager(request), response);
    }

    @PostMapping("/api/admin/login")
    public AdminLoginResponse loginAdmin(@Valid @RequestBody AdminLoginRequest request, HttpServletResponse response) {
        return apiSessionService.authenticateAdmin(accountService.loginAdmin(request), response);
    }

    @GetMapping("/api/auth/csrf")
    public CsrfTokenResponse csrf(HttpServletRequest request) {
        return new CsrfTokenResponse((String) request.getAttribute("csrfToken"));
    }

    @PostMapping({"/api/auth/refresh", "/api/auth/refreshRequest"})
    public AccessTokenResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        return apiSessionService.refresh(request, response);
    }

    @PostMapping({"/api/auth/logout", "/api/auth/logoutRequest"})
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        apiSessionService.logout(request, response);
    }
}
