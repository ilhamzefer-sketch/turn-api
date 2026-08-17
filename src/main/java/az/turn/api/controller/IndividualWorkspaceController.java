package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/individual-workspaces")
public class IndividualWorkspaceController {
    private final IndividualWorkspaceService workspaceService;
    private final RoomService roomService;
    private final RequestAuthenticationService authenticationService;

    public IndividualWorkspaceController(
            IndividualWorkspaceService workspaceService,
            RoomService roomService,
            RequestAuthenticationService authenticationService
    ) {
        this.workspaceService = workspaceService;
        this.roomService = roomService;
        this.authenticationService = authenticationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IndividualWorkspaceResponseDto create(
            @Valid @RequestBody IndividualWorkspaceCreateRequestDto request,
            Authentication auth
    ) {
        return workspaceService.create(userId(auth), request);
    }

    @GetMapping("/{workspaceId}")
    public IndividualWorkspaceResponseDto get(
            @PathVariable @Positive long workspaceId,
            Authentication auth
    ) {
        return workspaceService.get(workspaceId, userId(auth));
    }

    @PostMapping("/{workspaceId}/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponseDto createRoom(
            @PathVariable @Positive long workspaceId,
            @Valid @RequestBody RoomUpsertRequestDto request,
            Authentication auth
    ) {
        return roomService.createIndividualRoom(workspaceId, userId(auth), request);
    }

    private long userId(Authentication auth) {
        return authenticationService.requireUser(auth, AuthUserType.USER).userId();
    }
}
