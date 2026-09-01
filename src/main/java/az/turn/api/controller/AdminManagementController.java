package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin")
public class AdminManagementController {
    private final RequestAuthenticationService authenticationService;
    private final AdminAccountService adminAccountService;
    private final AdminManagementService adminManagementService;

    public AdminManagementController(
            RequestAuthenticationService authenticationService,
            AdminAccountService adminAccountService,
            AdminManagementService adminManagementService
    ) {
        this.authenticationService = authenticationService;
        this.adminAccountService = adminAccountService;
        this.adminManagementService = adminManagementService;
    }

    @GetMapping("/users")
    public AdminUserPageDto users(
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(required = false) @Size(max = 100) String name,
            @RequestParam(required = false) @Size(max = 40) String phone,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        if (search != null && !search.isBlank()) return adminManagementService.users(search, page, size);
        return adminManagementService.usersByNameAndPhone(name, phone, page, size);
    }

    @PostMapping("/users/{userId}/coins")
    public WalletTransactionDto creditCoins(
            @PathVariable @Positive long userId,
            @Valid @RequestBody AdminCoinCreditRequestDto request,
            Authentication authentication
    ) {
        AuthenticatedUser admin = requireAdmin(authentication);
        return adminManagementService.creditCoins(admin.username(), userId, request);
    }

    @PutMapping("/users/{userId}/password")
    public ResponseEntity<Void> changeUserPassword(
            @PathVariable @Positive long userId,
            @Valid @RequestBody AdminUserPasswordUpdateRequestDto request,
            Authentication authentication
    ) {
        AuthenticatedUser admin = requireAdmin(authentication);
        adminManagementService.changeUserPassword(admin.username(), userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/businesses")
    public AdminBusinessPageDto businesses(
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        return adminManagementService.businesses(search, page, size);
    }

    @PutMapping("/businesses/{businessId}/room-limit")
    public AdminBusinessDto increaseRoomLimit(
            @PathVariable @Positive long businessId,
            @Valid @RequestBody AdminRoomLimitUpdateRequestDto request,
            Authentication authentication
    ) {
        AuthenticatedUser admin = requireAdmin(authentication);
        return adminManagementService.increaseRoomLimit(admin.username(), businessId, request);
    }

    @GetMapping("/admins")
    public List<AdminAccountDto> admins(Authentication authentication) {
        requireAdmin(authentication);
        return adminAccountService.list();
    }

    @PostMapping("/admins")
    public ResponseEntity<AdminAccountDto> createAdmin(
            @Valid @RequestBody AdminAccountCreateRequestDto request,
            Authentication authentication
    ) {
        AuthenticatedUser actor = requireAdmin(authentication);
        AdminAccountDto created = adminAccountService.create(actor.username(), request);
        return ResponseEntity.created(URI.create("/api/admin/admins/" + created.id())).body(created);
    }

    private AuthenticatedUser requireAdmin(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.ADMIN);
    }
}
