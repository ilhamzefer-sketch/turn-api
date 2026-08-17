package az.turn.api;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support")
public class SupportController {
    private final SupportRequestService supportRequestService;
    private final RequestAuthenticationService authenticationService;

    public SupportController(
            SupportRequestService supportRequestService,
            RequestAuthenticationService authenticationService
    ) {
        this.supportRequestService = supportRequestService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/ownership-disputes")
    public OwnershipDisputeDto createDispute(@Valid @RequestBody OwnershipDisputeCreateRequestDto request) {
        return supportRequestService.createDispute(request);
    }

    @PostMapping("/phone-change-requests")
    public PhoneChangeRequestDto createPhoneChange(
            @Valid @RequestBody PhoneChangeCreateRequestDto request,
            Authentication authentication
    ) {
        long userId = authenticationService.requireUser(authentication, AuthUserType.USER).userId();
        return supportRequestService.createPhoneChange(userId, request);
    }

    @PostMapping("/account-deletion-requests")
    public AccountDeletionRequestDto createDeletion(Authentication authentication) {
        long userId = authenticationService.requireUser(authentication, AuthUserType.USER).userId();
        return supportRequestService.createDeletion(userId);
    }
}
