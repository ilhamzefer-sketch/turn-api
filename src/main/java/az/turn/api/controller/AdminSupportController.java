package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/admin/support")
public class AdminSupportController {
    private final SupportRequestService supportRequestService;
    private final RequestAuthenticationService authenticationService;

    public AdminSupportController(
            SupportRequestService supportRequestService,
            RequestAuthenticationService authenticationService
    ) {
        this.supportRequestService = supportRequestService;
        this.authenticationService = authenticationService;
    }

    @GetMapping("/ownership-disputes")
    public List<OwnershipDisputeDto> disputes(Authentication authentication) {
        requireAdmin(authentication);
        return supportRequestService.disputes();
    }

    @PostMapping("/ownership-disputes/{id}/resolve")
    public OwnershipDisputeDto resolveDispute(
            @PathVariable @Positive long id,
            @Valid @RequestBody OwnershipDisputeResolveRequestDto request,
            Authentication authentication
    ) {
        AuthenticatedUser admin = requireAdmin(authentication);
        return supportRequestService.resolveDispute(id, admin.username(), request);
    }

    @GetMapping("/phone-change-requests")
    public List<PhoneChangeRequestDto> phoneChanges(Authentication authentication) {
        requireAdmin(authentication);
        return supportRequestService.phoneChanges();
    }

    @PostMapping("/phone-change-requests/{id}/resolve")
    public PhoneChangeRequestDto resolvePhoneChange(
            @PathVariable @Positive long id,
            @Valid @RequestBody SupportResolutionRequestDto request,
            Authentication authentication
    ) {
        AuthenticatedUser admin = requireAdmin(authentication);
        return supportRequestService.resolvePhoneChange(id, admin.username(), request);
    }

    @GetMapping("/account-deletion-requests")
    public List<AccountDeletionRequestDto> deletions(Authentication authentication) {
        requireAdmin(authentication);
        return supportRequestService.deletions();
    }

    @PostMapping("/account-deletion-requests/{id}/resolve")
    public AccountDeletionRequestDto resolveDeletion(
            @PathVariable @Positive long id,
            @Valid @RequestBody SupportResolutionRequestDto request,
            Authentication authentication
    ) {
        AuthenticatedUser admin = requireAdmin(authentication);
        return supportRequestService.resolveDeletion(id, admin.username(), request);
    }

    @PostMapping("/users/{userId}/unlock")
    public void unlock(@PathVariable @Positive long userId, Authentication authentication) {
        AuthenticatedUser admin = requireAdmin(authentication);
        supportRequestService.unlock(userId, admin.username());
    }

    private AuthenticatedUser requireAdmin(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.ADMIN);
    }
}
