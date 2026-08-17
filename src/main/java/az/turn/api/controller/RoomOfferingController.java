package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/rooms/{roomId}/services")
public class RoomOfferingController {
    private final RoomOfferingService offeringService;
    private final RequestAuthenticationService authenticationService;

    public RoomOfferingController(
            RoomOfferingService offeringService,
            RequestAuthenticationService authenticationService
    ) {
        this.offeringService = offeringService;
        this.authenticationService = authenticationService;
    }

    @GetMapping
    public List<RoomServiceDto> list(@PathVariable @Positive long roomId, Authentication authentication) {
        return offeringService.list(roomId, userId(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomServiceDto create(
            @PathVariable @Positive long roomId,
            @Valid @RequestBody RoomServiceUpsertRequestDto request,
            Authentication authentication
    ) {
        return offeringService.create(roomId, userId(authentication), request);
    }

    @PutMapping("/{serviceId}")
    public RoomServiceDto update(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long serviceId,
            @Valid @RequestBody RoomServiceUpsertRequestDto request,
            Authentication authentication
    ) {
        return offeringService.update(roomId, serviceId, userId(authentication), request);
    }

    @DeleteMapping("/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long serviceId,
            Authentication authentication
    ) {
        offeringService.deactivate(roomId, serviceId, userId(authentication));
    }

    private long userId(Authentication authentication) {
        return authenticationService.requireUser(authentication, AuthUserType.USER).userId();
    }
}
