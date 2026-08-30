package az.turn.api;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminCredentialController {
    private final RequestAuthenticationService authenticationService;
    private final AdminAccountService adminAccountService;
    private final ApiSessionService apiSessionService;

    public AdminCredentialController(
            RequestAuthenticationService authenticationService,
            AdminAccountService adminAccountService,
            ApiSessionService apiSessionService
    ) {
        this.authenticationService = authenticationService;
        this.adminAccountService = adminAccountService;
        this.apiSessionService = apiSessionService;
    }

    @PutMapping("/api/admin/credentials")
    public AdminLoginResponse changeCredentials(
            @Valid @RequestBody AdminCredentialChangeRequestDto request,
            Authentication authentication,
            HttpServletResponse response
    ) {
        AuthenticatedUser admin = authenticationService.requireAdminCredentialChange(authentication);
        AdminLoginResponse changed = adminAccountService.changeRequiredCredentials(admin.username(), request);
        return apiSessionService.authenticateAdmin(changed, response);
    }
}
