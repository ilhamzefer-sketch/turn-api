package az.turn.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class AdminController {

    private final QueueService queueService;
    private final RequestAuthenticationService requestAuthenticationService;
    private final AdminPlatformService adminPlatformService;

    public AdminController(
            QueueService queueService,
            RequestAuthenticationService requestAuthenticationService,
            AdminPlatformService adminPlatformService
    ) {
        this.queueService = queueService;
        this.requestAuthenticationService = requestAuthenticationService;
        this.adminPlatformService = adminPlatformService;
    }

    @GetMapping("/api/admin/dashboard")
    public AdminDashboardResponse getAdminDashboard(
            @RequestParam(required = false) @Size(max = 150) String search,
            @RequestParam(required = false) @Pattern(regexp = "(?i)ALL|FERDI|KORPORATIV") String registrationType,
            @RequestParam(required = false) @Pattern(regexp = "(?i)ALL|PAID|UNPAID") String paymentStatus,
            @RequestParam(required = false) @Pattern(regexp = "\\d{4}-\\d{2}") String month,
            Authentication authentication
    ) {
        requestAuthenticationService.requireUser(authentication, AuthUserType.ADMIN);
        return queueService.getAdminDashboard(search, registrationType, paymentStatus, month);
    }

    @GetMapping("/api/admin/overview")
    public AdminPlatformOverviewDto overview(Authentication authentication) {
        requestAuthenticationService.requireUser(authentication, AuthUserType.ADMIN);
        return adminPlatformService.overview();
    }
}
