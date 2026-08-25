package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationAcceptanceService {
    private final RoomAssignmentRepository assignmentRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final BusinessMembershipService membershipService;
    private final RoomAssignmentService assignmentService;

    public InvitationAcceptanceService(
            RoomAssignmentRepository assignmentRepository,
            BusinessMembershipRepository membershipRepository,
            BusinessMembershipService membershipService,
            RoomAssignmentService assignmentService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.membershipRepository = membershipRepository;
        this.membershipService = membershipService;
        this.assignmentService = assignmentService;
    }

    @Transactional
    public RoomAssignmentDto acceptRoom(long assignmentId, long userId) {
        RoomAssignmentEntity assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment != null && assignment.getUser().getId().equals(userId)
                && assignment.getRoom().getBranch() != null) {
            long businessId = assignment.getRoom().getBranch().getBusiness().getId();
            membershipRepository.findByBusinessIdAndUserId(businessId, userId)
                    .filter(membership -> membership.getStatus() == BusinessMembershipStatus.PENDING_ACCEPTANCE)
                    .ifPresent(membership -> membershipService.accept(membership.getId(), userId));
        }
        return assignmentService.accept(assignmentId, userId);
    }
}
