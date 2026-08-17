package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CustomerBookingService {
    private final PlannedBookingRepository bookingRepository;
    private final ProviderAccessService accessService;
    private final PlannedBookingAvailabilityService availabilityService;
    private final PlannedBookingCommandSupport support;
    private final BookingAuditService auditService;
    private final PlannedBookingMapper mapper;
    private final SubscriptionGateService subscriptionGateService;
    private final RoomCustomerBlockService customerBlockService;
    private final Clock clock;

    public CustomerBookingService(
            PlannedBookingRepository bookingRepository,
            ProviderAccessService accessService,
            PlannedBookingAvailabilityService availabilityService,
            PlannedBookingCommandSupport support,
            BookingAuditService auditService,
            PlannedBookingMapper mapper,
            SubscriptionGateService subscriptionGateService,
            RoomCustomerBlockService customerBlockService,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.accessService = accessService;
        this.availabilityService = availabilityService;
        this.support = support;
        this.auditService = auditService;
        this.mapper = mapper;
        this.subscriptionGateService = subscriptionGateService;
        this.customerBlockService = customerBlockService;
        this.clock = clock;
    }

    @Transactional
    public PlannedBookingDto create(long userId, BookingCreateRequestDto request) {
        UserEntity user = accessService.requireActiveUser(userId);
        RoomEntity room = support.lockRoom(request.roomId());
        subscriptionGateService.requireRoomOperations(room);
        customerBlockService.requireAllowed(room.getId(), userId);
        availabilityService.requirePublicPlannedRoom(room);
        if (bookingRepository.hasActiveBookingForUser(
                room.getId(),
                userId,
                PlannedBookingStatus.ACTIVE
        )) {
            throw conflict("Bu otaqda artıq aktiv rezervasiyanız var.");
        }
        BookingTimeRange range = availabilityService.requireAvailable(room, request.startAt(), true, null);
        RoomServiceItemEntity service = support.resolveService(room, request.serviceId());
        PlannedBookingEntity booking = support.baseBooking(
                room,
                range,
                service,
                LiveQueueEntrySource.WEB,
                user
        );
        booking.setUser(user);
        booking.setActiveCustomerSlot(1);
        booking.setCustomerNote(support.normalizeOptional(request.customerNote()));
        PlannedBookingEntity saved = support.save(booking);
        auditService.record(
                saved,
                BookingAuditAction.CREATED,
                user,
                null,
                null,
                saved.getStartAt(),
                saved.getEndAt(),
                null,
                false
        );
        return mapper.toCustomerDto(saved);
    }

    @Transactional(readOnly = true)
    public PlannedBookingDto get(long bookingId, long userId) {
        accessService.requireActiveUser(userId);
        PlannedBookingEntity booking = support.requireBooking(bookingId);
        requireOwner(booking, userId);
        return mapper.toCustomerDto(booking);
    }

    @Transactional(readOnly = true)
    public List<PlannedBookingDto> history(long userId) {
        accessService.requireActiveUser(userId);
        return bookingRepository.findUserHistory(userId).stream().map(mapper::toCustomerDto).toList();
    }

    @Transactional
    public PlannedBookingDto cancel(long bookingId, long userId) {
        UserEntity user = accessService.requireActiveUser(userId);
        lockBookingRoom(bookingId);
        PlannedBookingEntity booking = support.requireBookingForUpdate(bookingId);
        requireOwner(booking, userId);
        requireBeforeCutoff(booking);
        LocalDateTime oldStart = booking.getStartAt();
        LocalDateTime oldEnd = booking.getEndAt();
        support.cancel(booking, BookingCancellationReason.CUSTOMER_CANCELLED, null);
        PlannedBookingEntity saved = support.save(booking);
        auditService.record(
                saved,
                BookingAuditAction.CANCELLED,
                user,
                oldStart,
                oldEnd,
                null,
                null,
                null,
                false
        );
        return mapper.toCustomerDto(saved);
    }

    @Transactional
    public PlannedBookingDto reschedule(
            long bookingId,
            long userId,
            BookingRescheduleRequestDto request
    ) {
        UserEntity user = accessService.requireActiveUser(userId);
        RoomEntity room = lockBookingRoom(bookingId);
        PlannedBookingEntity booking = support.requireBookingForUpdate(bookingId);
        requireOwner(booking, userId);
        support.requireActive(booking);
        requireBeforeCutoff(booking);
        LocalDateTime oldStart = booking.getStartAt();
        LocalDateTime oldEnd = booking.getEndAt();
        BookingTimeRange range = availabilityService.requireAvailable(
                room,
                request.startAt(),
                true,
                booking.getId()
        );
        support.applyRange(booking, range);
        PlannedBookingEntity saved = support.save(booking);
        auditService.record(
                saved,
                BookingAuditAction.RESCHEDULED,
                user,
                oldStart,
                oldEnd,
                saved.getStartAt(),
                saved.getEndAt(),
                null,
                false
        );
        return mapper.toCustomerDto(saved);
    }

    private RoomEntity lockBookingRoom(long bookingId) {
        return support.lockRoom(support.requireBookingRoomId(bookingId));
    }

    private void requireOwner(PlannedBookingEntity booking, long userId) {
        boolean directOwner = booking.getUser() != null && booking.getUser().getId().equals(userId);
        boolean linkedOwner = booking.getGuestContact() != null
                && booking.getGuestContact().getLinkedUser() != null
                && booking.getGuestContact().getLinkedUser().getId().equals(userId);
        if (!directOwner && !linkedOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu rezervasiyaya icazəniz yoxdur.");
        }
    }

    private void requireBeforeCutoff(PlannedBookingEntity booking) {
        support.requireActive(booking);
        LocalDateTime cutoff = booking.getStartAt()
                .minusMinutes(booking.getRoom().getCancellationCutoffMinutes());
        if (!LocalDateTime.now(clock).isBefore(cutoff)) {
            throw conflict("Özünəxidmət ləğv və dəyişiklik müddəti bitib. Otaq sahibi ilə əlaqə saxlayın.");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
