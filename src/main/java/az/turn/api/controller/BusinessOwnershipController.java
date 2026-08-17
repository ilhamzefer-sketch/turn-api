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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api")
public class BusinessOwnershipController {
    private final BusinessOwnershipTransferService transferService;
    private final RequestAuthenticationService authenticationService;

    public BusinessOwnershipController(
            BusinessOwnershipTransferService transferService,
            RequestAuthenticationService authenticationService
    ) {
        this.transferService = transferService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/businesses/{businessId}/ownership-transfers")
    public OwnershipTransferResponseDto create(
            @PathVariable @Positive long businessId,
            @Valid @RequestBody OwnershipTransferCreateRequestDto request,
            Authentication authentication
    ) {
        long userId = userId(authentication);
        return transferService.create(businessId, userId, request.toAdminUserId());
    }

    @GetMapping("/users/me/ownership-transfer-invitations")
    public List<OwnershipTransferResponseDto> invitations(Authentication authentication) {
        return transferService.invitations(userId(authentication));
    }

    @PostMapping("/ownership-transfers/{transferId}/respond")
    public OwnershipTransferResponseDto respond(
            @PathVariable @Positive long transferId,
            @RequestParam boolean accept,
            Authentication authentication
    ) {
        return transferService.respond(transferId, userId(authentication), accept);
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}
