package az.turn.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/users/me")
public class WorkspaceController {
    private final WorkspaceQueryService workspaceQueryService;
    private final BusinessMembershipService membershipService;
    private final RoomAssignmentService assignmentService;
    private final RequestAuthenticationService authenticationService;

    public WorkspaceController(
            WorkspaceQueryService workspaceQueryService,
            BusinessMembershipService membershipService,
            RoomAssignmentService assignmentService,
            RequestAuthenticationService authenticationService
    ) {
        this.workspaceQueryService = workspaceQueryService;
        this.membershipService = membershipService;
        this.assignmentService = assignmentService;
        this.authenticationService = authenticationService;
    }

    @GetMapping("/workspaces")
    public List<WorkspaceContextDto> workspaces(Authentication auth) {
        return workspaceQueryService.getContexts(userId(auth));
    }

    @GetMapping("/invitations")
    public UserInvitationsDto invitations(Authentication auth) {
        return workspaceQueryService.getInvitations(userId(auth));
    }

    @PostMapping("/business-invitations/{membershipId}/accept")
    public BusinessMembershipDto acceptBusiness(
            @PathVariable @Positive long membershipId,
            Authentication auth
    ) {
        return membershipService.accept(membershipId, userId(auth));
    }

    @PostMapping("/business-invitations/{membershipId}/reject")
    public BusinessMembershipDto rejectBusiness(
            @PathVariable @Positive long membershipId,
            Authentication auth
    ) {
        return membershipService.reject(membershipId, userId(auth));
    }

    @PostMapping("/room-invitations/{assignmentId}/accept")
    public RoomAssignmentDto acceptRoom(
            @PathVariable @Positive long assignmentId,
            Authentication auth
    ) {
        return assignmentService.accept(assignmentId, userId(auth));
    }

    @PostMapping("/room-invitations/{assignmentId}/reject")
    public RoomAssignmentDto rejectRoom(
            @PathVariable @Positive long assignmentId,
            Authentication auth
    ) {
        return assignmentService.reject(assignmentId, userId(auth));
    }

    @PutMapping("/room-assignments/{assignmentId}/phone-visibility")
    public RoomAssignmentDto updatePhoneVisibility(
            @PathVariable @Positive long assignmentId,
            @Valid @RequestBody RoomPhoneVisibilityRequestDto request,
            Authentication auth
    ) {
        return assignmentService.updatePhoneVisibility(assignmentId, userId(auth), request);
    }

    private long userId(Authentication auth) {
        return authenticationService.requireUser(auth, AuthUserType.USER).userId();
    }
}
