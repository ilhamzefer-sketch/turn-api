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
@RequestMapping("/api/rooms/{roomId}/customer-blocks")
public class RoomCustomerBlockController {
    private final RoomCustomerBlockService blockService;
    private final RequestAuthenticationService authenticationService;

    public RoomCustomerBlockController(
            RoomCustomerBlockService blockService,
            RequestAuthenticationService authenticationService
    ) {
        this.blockService = blockService;
        this.authenticationService = authenticationService;
    }

    @GetMapping
    public List<RoomCustomerBlockDto> list(
            @PathVariable @Positive long roomId,
            Authentication authentication
    ) {
        return blockService.list(roomId, userId(authentication));
    }

    @PostMapping
    public RoomCustomerBlockDto block(
            @PathVariable @Positive long roomId,
            @Valid @RequestBody RoomCustomerBlockRequestDto request,
            Authentication authentication
    ) {
        return blockService.block(roomId, userId(authentication), request);
    }

    @PostMapping("/{customerUserId}/revoke")
    public RoomCustomerBlockDto revoke(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long customerUserId,
            Authentication authentication
    ) {
        return blockService.revoke(roomId, customerUserId, userId(authentication));
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}
