package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RoomConfigurationService {
    private final RoomRepository roomRepository;
    private final ProviderAccessService accessService;
    private final RoomConfigurationValidator validator;
    private final ProviderWorkspaceMapper mapper;
    private final SubscriptionGateService subscriptionGateService;
    private final LiveQueueSessionProvisioningService liveQueueSessionProvisioningService;
    private final RoomDefaults roomDefaults;

    public RoomConfigurationService(
            RoomRepository roomRepository,
            ProviderAccessService accessService,
            RoomConfigurationValidator validator,
            ProviderWorkspaceMapper mapper,
            SubscriptionGateService subscriptionGateService,
            LiveQueueSessionProvisioningService liveQueueSessionProvisioningService,
            RoomDefaults roomDefaults
    ) {
        this.roomRepository = roomRepository;
        this.accessService = accessService;
        this.validator = validator;
        this.mapper = mapper;
        this.subscriptionGateService = subscriptionGateService;
        this.liveQueueSessionProvisioningService = liveQueueSessionProvisioningService;
        this.roomDefaults = roomDefaults;
    }

    @Transactional
    public RoomResponseDto update(
            long roomId,
            long userId,
            RoomConfigurationUpdateRequestDto request
    ) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        room.setDefaultSlotDurationMinutes(request.defaultSlotDurationMinutes());
        room.setAppointmentBufferMinutes(request.appointmentBufferMinutes());
        room.setBookingWindowDays(request.bookingWindowDays());
        room.setMinimumAdvanceMinutes(request.minimumAdvanceMinutes());
        room.setCancellationCutoffMinutes(request.cancellationCutoffMinutes());
        room.setLiveQueueResetPolicy(request.liveQueueResetPolicy());
        room.setLiveQueueResetLocalTime(request.liveQueueResetLocalTime());
        room.setLiveQueueResetIntervalMinutes(request.liveQueueResetIntervalMinutes());
        room.setLiveQueueMaxParticipants(request.liveQueueMaxParticipants());
        room.setLiveQueueAcceptingNewEntries(request.liveQueueAcceptingNewEntries());
        roomDefaults.normalizeModeConfiguration(room);
        validator.validateResetConfiguration(room);
        if (room.getReservationMode() == ReservationMode.PLANNED_BOOKING
                && (request.liveQueueResetPolicy() != null || request.liveQueueMaxParticipants() != null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Canlı növbə parametrləri planlı rezervasiya otağına tətbiq edilmir."
            );
        }
        if (room.getStatus() == RoomStatus.PUBLISHED) validator.validatePublishable(room);
        return mapper.toDto(roomRepository.save(room));
    }

    @Transactional
    public RoomResponseDto publish(long roomId, long userId) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        validator.validatePublishable(room);
        subscriptionGateService.requirePublish(room);
        room.setStatus(RoomStatus.PUBLISHED);
        RoomEntity saved = roomRepository.save(room);
        if (saved.getReservationMode() == ReservationMode.LIVE_QUEUE) {
            liveQueueSessionProvisioningService.resumeAutomaticSession(saved.getId());
        }
        return mapper.toDto(saved);
    }

    @Transactional
    public RoomResponseDto deactivate(long roomId, long userId) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        room.setStatus(RoomStatus.INACTIVE);
        return mapper.toDto(roomRepository.save(room));
    }
}
