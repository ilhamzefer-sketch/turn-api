package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoomService {
    private final RoomRepository roomRepository;
    private final RoomAssignmentRepository assignmentRepository;
    private final ProviderAccessService accessService;
    private final IndividualWorkspaceService individualWorkspaceService;
    private final ProviderInputService inputService;
    private final ProviderWorkspaceMapper mapper;
    private final RoomConfigurationValidator configurationValidator;
    private final LiveQueueSessionRepository liveQueueSessionRepository;
    private final Clock clock;

    public RoomService(
            RoomRepository roomRepository,
            RoomAssignmentRepository assignmentRepository,
            ProviderAccessService accessService,
            IndividualWorkspaceService individualWorkspaceService,
            ProviderInputService inputService,
            ProviderWorkspaceMapper mapper,
            RoomConfigurationValidator configurationValidator,
            LiveQueueSessionRepository liveQueueSessionRepository,
            Clock clock
    ) {
        this.roomRepository = roomRepository;
        this.assignmentRepository = assignmentRepository;
        this.accessService = accessService;
        this.individualWorkspaceService = individualWorkspaceService;
        this.inputService = inputService;
        this.mapper = mapper;
        this.configurationValidator = configurationValidator;
        this.liveQueueSessionRepository = liveQueueSessionRepository;
        this.clock = clock;
    }

    @Transactional
    public RoomResponseDto createBusinessRoom(
            long branchId,
            long userId,
            RoomUpsertRequestDto request
    ) {
        BranchEntity branch = accessService.requireManagedBranch(branchId, userId);
        UserEntity creator = accessService.requireActiveUser(userId);
        RoomEntity room = new RoomEntity();
        room.setBranch(branch);
        room.setCreatedByUser(creator);
        apply(room, request, branch.getTimezone(), false);
        applyConfigurationDefaults(room);
        room.setStatus(RoomStatus.DRAFT);
        return mapper.toDto(roomRepository.save(room));
    }

    @Transactional
    public RoomResponseDto createIndividualRoom(
            long workspaceId,
            long userId,
            RoomUpsertRequestDto request
    ) {
        IndividualWorkspaceEntity workspace = individualWorkspaceService.requireOwned(workspaceId, userId);
        if (roomRepository.findByIndividualWorkspaceId(workspaceId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Şəxsi workspace üçün artıq otaq yaradılıb.");
        }
        UserEntity creator = accessService.requireActiveUser(userId);
        RoomEntity room = new RoomEntity();
        room.setIndividualWorkspace(workspace);
        room.setCreatedByUser(creator);
        apply(room, request, workspace.getTimezone(), true);
        applyConfigurationDefaults(room);
        room.setStatus(RoomStatus.DRAFT);
        RoomEntity saved = roomRepository.save(room);
        assignmentRepository.save(activeOwnerAssignment(saved, creator));
        return mapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<RoomResponseDto> listBusinessRooms(long businessId, long userId) {
        accessService.requireBusinessManager(businessId, userId);
        return roomRepository.findByBranchBusinessIdOrderByCreatedAtAsc(businessId)
                .stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RoomResponseDto get(long roomId, long userId) {
        return mapper.toDto(accessService.requireRoomViewer(roomId, userId));
    }

    @Transactional
    public RoomResponseDto update(long roomId, long userId, RoomUpsertRequestDto request) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        if (room.getReservationMode() != request.reservationMode()
                && liveQueueSessionRepository.findByRoomIdAndOpenSlot(roomId, 1).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Açıq canlı növbə sessiyası bağlanmadan otaq rejimi dəyişdirilə bilməz."
            );
        }
        String fallbackTimezone = room.getBranch() == null
                ? room.getIndividualWorkspace().getTimezone()
                : room.getBranch().getTimezone();
        apply(room, request, fallbackTimezone, room.getIndividualWorkspace() != null);
        if (room.getStatus() == RoomStatus.PUBLISHED) configurationValidator.validatePublishable(room);
        return mapper.toDto(roomRepository.save(room));
    }

    @Transactional
    public void archive(long roomId, long userId) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        if (liveQueueSessionRepository.findByRoomIdAndOpenSlot(roomId, 1).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Açıq canlı növbə sessiyası bağlanmadan otaq arxivləşdirilə bilməz."
            );
        }
        room.setStatus(RoomStatus.ARCHIVED);
        room.setVisibility(RoomVisibility.UNLISTED);
        room.setArchivedAt(LocalDateTime.now(clock));
        roomRepository.save(room);
    }

    private void apply(RoomEntity room, RoomUpsertRequestDto request, String fallbackTimezone, boolean individual) {
        if (!individual && (request.personalPublicAddress() != null
                || request.personalLatitude() != null || request.personalLongitude() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Biznes otağının ünvanı filialdan götürülür.");
        }
        room.setName(inputService.required(request.name(), "Otaq adı mütləqdir."));
        room.setRoomNumberOrCode(inputService.optional(request.roomNumberOrCode()));
        room.setDescription(inputService.optional(request.description()));
        room.setNotes(inputService.optional(request.notes()));
        room.setTimezone(inputService.timezone(request.timezone(), fallbackTimezone));
        room.setReservationMode(request.reservationMode());
        if (request.reservationMode() == ReservationMode.PLANNED_BOOKING) {
            room.setLiveQueueResetPolicy(null);
            room.setLiveQueueResetLocalTime(null);
            room.setLiveQueueResetIntervalMinutes(null);
            room.setLiveQueueMaxParticipants(null);
        }
        room.setDefaultSlotDurationMinutes(request.defaultSlotDurationMinutes());
        room.setVisibility(request.visibility());
        room.setPersonalPublicAddress(individual ? inputService.optional(request.personalPublicAddress()) : null);
        room.setPersonalLatitude(individual ? request.personalLatitude() : null);
        room.setPersonalLongitude(individual ? request.personalLongitude() : null);
    }

    private void applyConfigurationDefaults(RoomEntity room) {
        room.setAppointmentBufferMinutes(0);
        room.setBookingWindowDays(30);
        room.setMinimumAdvanceMinutes(30);
        room.setCancellationCutoffMinutes(0);
        room.setLiveQueueAcceptingNewEntries(true);
    }

    private RoomAssignmentEntity activeOwnerAssignment(RoomEntity room, UserEntity owner) {
        RoomAssignmentEntity assignment = new RoomAssignmentEntity();
        assignment.setRoom(room);
        assignment.setUser(owner);
        assignment.setRole(RoomRole.ROOM_OWNER);
        assignment.setStatus(RoomAssignmentStatus.ACTIVE);
        assignment.setInvitedByUser(owner);
        assignment.setRespondedAt(LocalDateTime.now(clock));
        return assignment;
    }
}
