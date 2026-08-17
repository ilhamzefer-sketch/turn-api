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
@RequestMapping("/api/businesses")
public class BusinessController {
    private final BusinessService businessService;
    private final BusinessMembershipService membershipService;
    private final BranchService branchService;
    private final RoomService roomService;
    private final RequestAuthenticationService authenticationService;

    public BusinessController(
            BusinessService businessService,
            BusinessMembershipService membershipService,
            BranchService branchService,
            RoomService roomService,
            RequestAuthenticationService authenticationService
    ) {
        this.businessService = businessService;
        this.membershipService = membershipService;
        this.branchService = branchService;
        this.roomService = roomService;
        this.authenticationService = authenticationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessResponseDto create(@Valid @RequestBody BusinessUpsertRequestDto request, Authentication auth) {
        return businessService.create(userId(auth), request);
    }

    @GetMapping
    public List<BusinessResponseDto> getMine(Authentication auth) {
        return businessService.getMine(userId(auth));
    }

    @GetMapping("/{businessId}")
    public BusinessResponseDto get(@PathVariable @Positive long businessId, Authentication auth) {
        return businessService.get(businessId, userId(auth));
    }

    @PutMapping("/{businessId}")
    public BusinessResponseDto update(
            @PathVariable @Positive long businessId,
            @Valid @RequestBody BusinessUpsertRequestDto request,
            Authentication auth
    ) {
        return businessService.update(businessId, userId(auth), request);
    }

    @DeleteMapping("/{businessId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable @Positive long businessId, Authentication auth) {
        businessService.archive(businessId, userId(auth));
    }

    @GetMapping("/{businessId}/members")
    public List<BusinessMembershipDto> members(@PathVariable @Positive long businessId, Authentication auth) {
        return membershipService.list(businessId, userId(auth));
    }

    @PostMapping("/{businessId}/members/by-phone")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessMembershipDto inviteMember(
            @PathVariable @Positive long businessId,
            @Valid @RequestBody BusinessMemberInviteRequestDto request,
            Authentication auth
    ) {
        return membershipService.invite(businessId, userId(auth), request);
    }

    @PutMapping("/{businessId}/members/{membershipId}")
    public BusinessMembershipDto updateMember(
            @PathVariable @Positive long businessId,
            @PathVariable @Positive long membershipId,
            @Valid @RequestBody BusinessMembershipUpdateRequestDto request,
            Authentication auth
    ) {
        return membershipService.updateRole(businessId, membershipId, userId(auth), request);
    }

    @DeleteMapping("/{businessId}/members/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable @Positive long businessId,
            @PathVariable @Positive long membershipId,
            Authentication auth
    ) {
        membershipService.remove(businessId, membershipId, userId(auth));
    }

    @GetMapping("/{businessId}/branches")
    public List<BranchResponseDto> branches(@PathVariable @Positive long businessId, Authentication auth) {
        return branchService.list(businessId, userId(auth));
    }

    @PostMapping("/{businessId}/branches")
    @ResponseStatus(HttpStatus.CREATED)
    public BranchResponseDto createBranch(
            @PathVariable @Positive long businessId,
            @Valid @RequestBody BranchUpsertRequestDto request,
            Authentication auth
    ) {
        return branchService.create(businessId, userId(auth), request);
    }

    @GetMapping("/{businessId}/rooms")
    public List<RoomResponseDto> rooms(@PathVariable @Positive long businessId, Authentication auth) {
        return roomService.listBusinessRooms(businessId, userId(auth));
    }

    private long userId(Authentication auth) {
        return authenticationService.requireUser(auth, AuthUserType.USER).userId();
    }
}
