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
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;
    private final RoomAssignmentService assignmentService;
    private final RequestAuthenticationService authenticationService;

    public RoomController(
            RoomService roomService,
            RoomAssignmentService assignmentService,
            RequestAuthenticationService authenticationService
    ) {
        this.roomService = roomService;
        this.assignmentService = assignmentService;
        this.authenticationService = authenticationService;
    }

    @GetMapping("/{roomId}")
    public RoomResponseDto get(@PathVariable @Positive long roomId, Authentication auth) {
        return roomService.get(roomId, userId(auth));
    }

    @PutMapping("/{roomId}")
    public RoomResponseDto update(
            @PathVariable @Positive long roomId,
            @Valid @RequestBody RoomUpsertRequestDto request,
            Authentication auth
    ) {
        return roomService.update(roomId, userId(auth), request);
    }

    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable @Positive long roomId, Authentication auth) {
        roomService.archive(roomId, userId(auth));
    }

    @GetMapping("/{roomId}/assignments")
    public List<RoomAssignmentDto> assignments(@PathVariable @Positive long roomId, Authentication auth) {
        return assignmentService.list(roomId, userId(auth));
    }

    @PostMapping("/{roomId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomAssignmentDto invite(
            @PathVariable @Positive long roomId,
            @Valid @RequestBody RoomAssignmentInviteRequestDto request,
            Authentication auth
    ) {
        return assignmentService.invite(roomId, userId(auth), request);
    }

    @DeleteMapping("/{roomId}/assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @PathVariable @Positive long roomId,
            @PathVariable @Positive long assignmentId,
            Authentication auth
    ) {
        assignmentService.revoke(roomId, assignmentId, userId(auth));
    }

    private long userId(Authentication auth) {
        return authenticationService.requireUser(auth, AuthUserType.USER).userId();
    }
}
