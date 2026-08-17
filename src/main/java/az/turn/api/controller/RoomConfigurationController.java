package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/rooms")
public class RoomConfigurationController {
    private final RoomConfigurationService configurationService;
    private final RequestAuthenticationService authenticationService;

    public RoomConfigurationController(
            RoomConfigurationService configurationService,
            RequestAuthenticationService authenticationService
    ) {
        this.configurationService = configurationService;
        this.authenticationService = authenticationService;
    }

    @PutMapping("/{roomId}/configuration")
    public RoomResponseDto update(
            @PathVariable @Positive long roomId,
            @Valid @RequestBody RoomConfigurationUpdateRequestDto request,
            Authentication authentication
    ) {
        return configurationService.update(roomId, userId(authentication), request);
    }

    @PostMapping("/{roomId}/publish")
    public RoomResponseDto publish(@PathVariable @Positive long roomId, Authentication authentication) {
        return configurationService.publish(roomId, userId(authentication));
    }

    @PostMapping("/{roomId}/deactivate")
    public RoomResponseDto deactivate(@PathVariable @Positive long roomId, Authentication authentication) {
        return configurationService.deactivate(roomId, userId(authentication));
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}
