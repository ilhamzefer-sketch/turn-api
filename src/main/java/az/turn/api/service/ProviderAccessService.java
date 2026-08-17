package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProviderAccessService {
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final BranchRepository branchRepository;
    private final RoomRepository roomRepository;
    private final RoomAssignmentRepository assignmentRepository;

    public ProviderAccessService(
            UserRepository userRepository,
            BusinessRepository businessRepository,
            BusinessMembershipRepository membershipRepository,
            BranchRepository branchRepository,
            RoomRepository roomRepository,
            RoomAssignmentRepository assignmentRepository
    ) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.membershipRepository = membershipRepository;
        this.branchRepository = branchRepository;
        this.roomRepository = roomRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public UserEntity requireActiveUser(long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "İstifadəçi tapılmadı."));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Aktiv istifadəçi hesabı tələb olunur.");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public BusinessEntity requireBusinessManager(long businessId, long userId) {
        BusinessEntity business = requireActiveBusiness(businessId);
        BusinessMembershipEntity membership = membershipRepository.findByBusinessIdAndUserId(businessId, userId)
                .orElseThrow(this::forbidden);
        if (membership.getStatus() != BusinessMembershipStatus.ACTIVE
                || membership.getRole() == BusinessRole.EMPLOYEE) {
            throw forbidden();
        }
        return business;
    }

    @Transactional(readOnly = true)
    public BusinessEntity requirePrimaryOwner(long businessId, long userId) {
        BusinessEntity business = requireActiveBusiness(businessId);
        if (!business.getPrimaryOwnerUser().getId().equals(userId)) {
            throw forbidden();
        }
        return business;
    }

    @Transactional(readOnly = true)
    public BranchEntity requireManagedBranch(long branchId, long userId) {
        BranchEntity branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Filial tapılmadı."));
        requireBusinessManager(branch.getBusiness().getId(), userId);
        if (branch.getStatus() == ProviderStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Filial arxivdədir.");
        }
        return branch;
    }

    @Transactional(readOnly = true)
    public RoomEntity requireRoomViewer(long roomId, long userId) {
        RoomEntity room = findRoom(roomId);
        if (!canManageRoom(room, userId)) throw forbidden();
        return room;
    }

    @Transactional(readOnly = true)
    public RoomEntity requireEditableRoom(long roomId, long userId) {
        RoomEntity room = requireRoomViewer(roomId, userId);
        if (room.getStatus() == RoomStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Otaq arxivdədir.");
        }
        return room;
    }

    @Transactional(readOnly = true)
    public RoomEntity requireAssignmentManager(long roomId, long userId) {
        RoomEntity room = findRoom(roomId);
        if (room.getBranch() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Şəxsi otağa əlavə sahib təyin edilmir.");
        }
        requireBusinessManager(room.getBranch().getBusiness().getId(), userId);
        return room;
    }

    private BusinessEntity requireActiveBusiness(long businessId) {
        BusinessEntity business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Biznes tapılmadı."));
        if (business.getStatus() == ProviderStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Biznes arxivdədir.");
        }
        return business;
    }

    private RoomEntity findRoom(long roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq tapılmadı."));
    }

    private boolean canManageRoom(RoomEntity room, long userId) {
        if (room.getIndividualWorkspace() != null) {
            return room.getIndividualWorkspace().getOwnerUser().getId().equals(userId);
        }
        long businessId = room.getBranch().getBusiness().getId();
        BusinessMembershipEntity membership = membershipRepository.findByBusinessIdAndUserId(businessId, userId).orElse(null);
        if (membership != null && membership.getStatus() == BusinessMembershipStatus.ACTIVE
                && membership.getRole() != BusinessRole.EMPLOYEE) {
            return true;
        }
        return assignmentRepository.findByRoomIdAndUserId(room.getId(), userId)
                .filter(assignment -> assignment.getStatus() == RoomAssignmentStatus.ACTIVE)
                .isPresent();
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu əməliyyat üçün icazəniz yoxdur.");
    }
}
