package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoomAssignmentService {
    private final RoomAssignmentRepository assignmentRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final ProviderAccessService accessService;
    private final ProviderWorkspaceMapper mapper;
    private final Clock clock;

    public RoomAssignmentService(
            RoomAssignmentRepository assignmentRepository,
            BusinessMembershipRepository membershipRepository,
            UserRepository userRepository,
            RoomRepository roomRepository,
            ProviderAccessService accessService,
            ProviderWorkspaceMapper mapper,
            Clock clock
    ) {
        this.assignmentRepository = assignmentRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.accessService = accessService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public RoomAssignmentDto invite(
            long roomId,
            long actorUserId,
            RoomAssignmentInviteRequestDto request
    ) {
        RoomEntity room = accessService.requireAssignmentManager(roomId, actorUserId);
        UserEntity actor = accessService.requireActiveUser(actorUserId);
        UserEntity invitedUser = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "İstifadəçi tapılmadı."));
        long businessId = room.getBranch().getBusiness().getId();
        BusinessMembershipEntity membership = membershipRepository.findByBusinessIdAndUserId(businessId, invitedUser.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "İstifadəçi əvvəlcə biznes üzvlüyünə dəvət edilməlidir."
                ));
        if (membership.getStatus() != BusinessMembershipStatus.ACTIVE
                && membership.getStatus() != BusinessMembershipStatus.PENDING_ACCEPTANCE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Biznes üzvlüyü aktiv və ya gözləyən vəziyyətdə deyil.");
        }

        RoomAssignmentEntity assignment = assignmentRepository.findByRoomIdAndUserId(roomId, invitedUser.getId())
                .orElseGet(RoomAssignmentEntity::new);
        if (assignment.getId() != null && assignment.getStatus() != RoomAssignmentStatus.REJECTED
                && assignment.getStatus() != RoomAssignmentStatus.REVOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu istifadəçi artıq otağa əlavə edilib və ya dəvət gözləyir.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        assignment.setRoom(room);
        assignment.setUser(invitedUser);
        assignment.setRole(RoomRole.ROOM_OWNER);
        assignment.setStatus(RoomAssignmentStatus.PENDING_ACCEPTANCE);
        assignment.setInvitedByUser(actor);
        assignment.setInvitedAt(now);
        assignment.setRespondedAt(null);
        assignment.setRevokedAt(null);
        return mapper.toDto(assignmentRepository.save(assignment));
    }

    @Transactional(readOnly = true)
    public List<RoomAssignmentDto> list(long roomId, long actorUserId) {
        accessService.requireRoomViewer(roomId, actorUserId);
        return assignmentRepository.findByRoomIdOrderByCreatedAtAsc(roomId)
                .stream().map(mapper::toDto).toList();
    }

    @Transactional
    public RoomAssignmentDto accept(long assignmentId, long userId) {
        accessService.requireActiveUser(userId);
        RoomAssignmentEntity assignment = requireUserInvitation(assignmentId, userId);
        long businessId = assignment.getRoom().getBranch().getBusiness().getId();
        BusinessMembershipEntity membership = membershipRepository.findByBusinessIdAndUserId(businessId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Aktiv biznes üzvlüyü tələb olunur."));
        if (membership.getStatus() != BusinessMembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Əvvəlcə biznes dəvətini qəbul edin.");
        }
        assignment.setStatus(RoomAssignmentStatus.ACTIVE);
        assignment.setRespondedAt(LocalDateTime.now(clock));
        return mapper.toDto(assignmentRepository.save(assignment));
    }

    @Transactional
    public RoomAssignmentDto reject(long assignmentId, long userId) {
        accessService.requireActiveUser(userId);
        RoomAssignmentEntity assignment = requireUserInvitation(assignmentId, userId);
        assignment.setStatus(RoomAssignmentStatus.REJECTED);
        assignment.setRespondedAt(LocalDateTime.now(clock));
        return mapper.toDto(assignmentRepository.save(assignment));
    }

    @Transactional
    public RoomAssignmentDto updatePhoneVisibility(
            long assignmentId,
            long userId,
            RoomPhoneVisibilityRequestDto request
    ) {
        accessService.requireActiveUser(userId);
        RoomAssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq təyinatı tapılmadı."));
        if (!assignment.getUser().getId().equals(userId) || assignment.getStatus() != RoomAssignmentStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu otaq təyinatını dəyişmək icazəniz yoxdur.");
        }
        assignment.setShowPhonePublicly(request.showPhonePublicly());
        return mapper.toDto(assignmentRepository.save(assignment));
    }

    @Transactional
    public void revoke(long roomId, long assignmentId, long actorUserId) {
        RoomEntity room = accessService.requireAssignmentManager(roomId, actorUserId);
        RoomAssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq təyinatı tapılmadı."));
        if (!assignment.getRoom().getId().equals(roomId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq təyinatı tapılmadı.");
        }
        boolean wasActive = assignment.getStatus() == RoomAssignmentStatus.ACTIVE;
        assignment.setStatus(RoomAssignmentStatus.REVOKED);
        assignment.setRevokedAt(LocalDateTime.now(clock));
        assignmentRepository.save(assignment);
        if (wasActive && assignmentRepository.countByRoomIdAndStatus(roomId, RoomAssignmentStatus.ACTIVE) == 0
                && room.getStatus() == RoomStatus.PUBLISHED) {
            room.setStatus(RoomStatus.INACTIVE);
            room.setVisibility(RoomVisibility.UNLISTED);
            roomRepository.save(room);
        }
    }

    private RoomAssignmentEntity requireUserInvitation(long assignmentId, long userId) {
        RoomAssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq dəvəti tapılmadı."));
        if (!assignment.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu dəvət sizə aid deyil.");
        }
        if (assignment.getStatus() != RoomAssignmentStatus.PENDING_ACCEPTANCE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Otaq dəvəti artıq cavablandırılıb.");
        }
        return assignment;
    }
}
