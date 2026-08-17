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

@RestController
@Validated
@RequestMapping("/api/branches")
public class BranchController {
    private final BranchService branchService;
    private final RoomService roomService;
    private final RequestAuthenticationService authenticationService;

    public BranchController(
            BranchService branchService,
            RoomService roomService,
            RequestAuthenticationService authenticationService
    ) {
        this.branchService = branchService;
        this.roomService = roomService;
        this.authenticationService = authenticationService;
    }

    @GetMapping("/{branchId}")
    public BranchResponseDto get(@PathVariable @Positive long branchId, Authentication auth) {
        return branchService.get(branchId, userId(auth));
    }

    @PutMapping("/{branchId}")
    public BranchResponseDto update(
            @PathVariable @Positive long branchId,
            @Valid @RequestBody BranchUpsertRequestDto request,
            Authentication auth
    ) {
        return branchService.update(branchId, userId(auth), request);
    }

    @DeleteMapping("/{branchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable @Positive long branchId, Authentication auth) {
        branchService.archive(branchId, userId(auth));
    }

    @PostMapping("/{branchId}/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponseDto createRoom(
            @PathVariable @Positive long branchId,
            @Valid @RequestBody RoomUpsertRequestDto request,
            Authentication auth
    ) {
        return roomService.createBusinessRoom(branchId, userId(auth), request);
    }

    private long userId(Authentication auth) {
        return authenticationService.requireUser(auth, AuthUserType.USER).userId();
    }
}
