package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class WorkspaceQueryService {
    private final UserRepository userRepository;
    private final IndividualWorkspaceRepository workspaceRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final RoomAssignmentRepository assignmentRepository;
    private final ProviderWorkspaceMapper mapper;

    public WorkspaceQueryService(
            UserRepository userRepository,
            IndividualWorkspaceRepository workspaceRepository,
            BusinessMembershipRepository membershipRepository,
            RoomAssignmentRepository assignmentRepository,
            ProviderWorkspaceMapper mapper
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.assignmentRepository = assignmentRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceContextDto> getContexts(long userId) {
        UserEntity user = userRepository.findById(userId).orElseThrow();
        List<WorkspaceContextDto> contexts = new ArrayList<>();
        contexts.add(new WorkspaceContextDto(
                WorkspaceContextType.CUSTOMER,
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                "CUSTOMER"
        ));
        workspaceRepository.findByOwnerUserId(userId)
                .filter(workspace -> workspace.getStatus() == ProviderStatus.ACTIVE)
                .ifPresent(workspace -> contexts.add(new WorkspaceContextDto(
                        WorkspaceContextType.INDIVIDUAL,
                        workspace.getId(),
                        workspace.getName(),
                        "OWNER"
                )));
        membershipRepository.findByUserIdAndStatusOrderByInvitedAtAsc(userId, BusinessMembershipStatus.ACTIVE)
                .stream()
                .filter(membership -> membership.getBusiness().getStatus() == ProviderStatus.ACTIVE)
                .filter(membership -> membership.getRole() != BusinessRole.EMPLOYEE)
                .map(membership -> new WorkspaceContextDto(
                        WorkspaceContextType.BUSINESS,
                        membership.getBusiness().getId(),
                        membership.getBusiness().getName(),
                        membership.getRole().name()
                )).forEach(contexts::add);
        assignmentRepository.findByUserIdAndStatus(userId, RoomAssignmentStatus.ACTIVE)
                .stream()
                .filter(assignment -> assignment.getRoom().getStatus() != RoomStatus.ARCHIVED)
                .filter(assignment -> assignment.getRoom().getIndividualWorkspace() == null)
                .map(assignment -> new WorkspaceContextDto(
                        WorkspaceContextType.ROOM,
                        assignment.getRoom().getId(),
                        assignment.getRoom().getName(),
                        assignment.getRole().name()
                )).forEach(contexts::add);
        return List.copyOf(contexts);
    }

    @Transactional(readOnly = true)
    public UserInvitationsDto getInvitations(long userId) {
        List<BusinessMembershipDto> memberships = membershipRepository
                .findByUserIdAndStatusOrderByInvitedAtAsc(userId, BusinessMembershipStatus.PENDING_ACCEPTANCE)
                .stream().map(mapper::toDto).toList();
        List<RoomAssignmentDto> assignments = assignmentRepository
                .findByUserIdAndStatusOrderByCreatedAtAsc(userId, RoomAssignmentStatus.PENDING_ACCEPTANCE)
                .stream().map(mapper::toDto).toList();
        return new UserInvitationsDto(memberships, assignments);
    }
}
