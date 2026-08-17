package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoomBookingService {
    private final PlannedBookingRepository bookingRepository;
    private final ProviderAccessService accessService;
    private final PlannedBookingAvailabilityService availabilityService;
    private final PlannedBookingCommandSupport support;
    private final GuestContactService guestContactService;
    private final BookingAuditService auditService;
    private final PlannedBookingMapper mapper;
    private final SubscriptionGateService subscriptionGateService;
    private final Clock clock;

    public RoomBookingService(
            PlannedBookingRepository bookingRepository,
            ProviderAccessService accessService,
            PlannedBookingAvailabilityService availabilityService,
            PlannedBookingCommandSupport support,
            GuestContactService guestContactService,
            BookingAuditService auditService,
            PlannedBookingMapper mapper,
            SubscriptionGateService subscriptionGateService,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.accessService = accessService;
        this.availabilityService = availabilityService;
        this.support = support;
        this.guestContactService = guestContactService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.subscriptionGateService = subscriptionGateService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlannedBookingDto> list(long roomId, long userId, LocalDate date) {
        accessService.requireRoomViewer(roomId, userId);
        return bookingRepository
                .findByRoomIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        roomId,
                        date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay()
                )
                .stream().map(mapper::toDto).toList();
    }

    @Transactional
    public PlannedBookingDto createManual(
            long roomId,
            long userId,
            BookingManualCreateRequestDto request
    ) {
        accessService.requireEditableRoom(roomId, userId);
        RoomEntity room = support.lockRoom(roomId);
        subscriptionGateService.requireRoomOperations(room);
        validateManualSource(request.source());
        BookingTimeRange range = availabilityService.requireAvailable(room, request.startAt(), false, null);
        UserEntity creator = accessService.requireActiveUser(userId);
        GuestContactEntity guest = guestContactService.resolve(request.displayName(), request.phone());
        RoomServiceItemEntity service = support.resolveService(room, request.serviceId());
        PlannedBookingEntity booking = support.baseBooking(room, range, service, request.source(), creator);
        booking.setGuestContact(guest);
        booking.setInternalNote(support.normalizeOptional(request.internalNote()));
        PlannedBookingEntity saved = support.save(booking);
        record(
                saved,
                BookingAuditAction.CREATED,
                creator,
                null,
                null,
                saved.getStartAt(),
                saved.getEndAt(),
                null,
                false
        );
        return mapper.toDto(saved);
    }

    @Transactional
    public PlannedBookingDto cancel(
            long roomId,
            long bookingId,
            long userId,
            BookingOperatorCancelRequestDto request
    ) {
        requireParticipantInformed(request.participantInformed());
        UserEntity actor = lockOperator(roomId, userId);
        PlannedBookingEntity booking = support.requireRoomBookingForUpdate(roomId, bookingId);
        LocalDateTime oldStart = booking.getStartAt();
        LocalDateTime oldEnd = booking.getEndAt();
        support.cancel(booking, BookingCancellationReason.OWNER_CANCELLED, request.reason());
        PlannedBookingEntity saved = support.save(booking);
        record(
                saved,
                BookingAuditAction.CANCELLED,
                actor,
                oldStart,
                oldEnd,
                null,
                null,
                request.reason().trim(),
                true
        );
        return mapper.toDto(saved);
    }

    @Transactional
    public PlannedBookingDto noShow(long roomId, long bookingId, long userId) {
        UserEntity actor = lockOperator(roomId, userId);
        PlannedBookingEntity booking = support.requireRoomBookingForUpdate(roomId, bookingId);
        if (LocalDateTime.now(clock).isBefore(booking.getStartAt())) {
            throw conflict("Rezervasiya vaxtından əvvəl gəlməmə kimi işarələnə bilməz.");
        }
        LocalDateTime oldStart = booking.getStartAt();
        LocalDateTime oldEnd = booking.getEndAt();
        support.cancel(booking, BookingCancellationReason.NO_SHOW, "İştirakçı gəlmədi.");
        PlannedBookingEntity saved = support.save(booking);
        record(
                saved,
                BookingAuditAction.CANCELLED,
                actor,
                oldStart,
                oldEnd,
                null,
                null,
                "NO_SHOW",
                false
        );
        return mapper.toDto(saved);
    }

    @Transactional
    public PlannedBookingDto complete(long roomId, long bookingId, long userId) {
        UserEntity actor = lockOperator(roomId, userId);
        PlannedBookingEntity booking = support.requireRoomBookingForUpdate(roomId, bookingId);
        if (LocalDateTime.now(clock).isBefore(booking.getStartAt())) {
            throw conflict("Rezervasiya vaxtından əvvəl tamamlanmış kimi işarələnə bilməz.");
        }
        LocalDateTime oldStart = booking.getStartAt();
        LocalDateTime oldEnd = booking.getEndAt();
        support.complete(booking);
        PlannedBookingEntity saved = support.save(booking);
        record(
                saved,
                BookingAuditAction.COMPLETED,
                actor,
                oldStart,
                oldEnd,
                null,
                null,
                null,
                false
        );
        return mapper.toDto(saved);
    }

    @Transactional
    public PlannedBookingDto reschedule(
            long roomId,
            long bookingId,
            long userId,
            BookingOperatorRescheduleRequestDto request
    ) {
        requireParticipantInformed(request.participantInformed());
        UserEntity actor = lockOperator(roomId, userId);
        PlannedBookingEntity booking = support.requireRoomBookingForUpdate(roomId, bookingId);
        support.requireActive(booking);
        LocalDateTime oldStart = booking.getStartAt();
        LocalDateTime oldEnd = booking.getEndAt();
        BookingTimeRange range = availabilityService.requireAvailable(
                booking.getRoom(),
                request.startAt(),
                false,
                booking.getId()
        );
        support.applyRange(booking, range);
        PlannedBookingEntity saved = support.save(booking);
        record(
                saved,
                BookingAuditAction.RESCHEDULED,
                actor,
                oldStart,
                oldEnd,
                saved.getStartAt(),
                saved.getEndAt(),
                null,
                true
        );
        return mapper.toDto(saved);
    }

    private UserEntity lockOperator(long roomId, long userId) {
        accessService.requireEditableRoom(roomId, userId);
        support.lockRoom(roomId);
        return accessService.requireActiveUser(userId);
    }

    private void validateManualSource(LiveQueueEntrySource source) {
        if (source == LiveQueueEntrySource.WEB || source == LiveQueueEntrySource.QR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manual rezervasiya üçün owner mənbəyi seçilməlidir.");
        }
    }

    private void requireParticipantInformed(Boolean participantInformed) {
        if (!Boolean.TRUE.equals(participantInformed)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rezervasiya dəyişikliklərindən əvvəl iştirakçı məlumatlandırılmalıdır."
            );
        }
    }

    private void record(
            PlannedBookingEntity booking,
            BookingAuditAction action,
            UserEntity actor,
            LocalDateTime oldStart,
            LocalDateTime oldEnd,
            LocalDateTime newStart,
            LocalDateTime newEnd,
            String reason,
            boolean informed
    ) {
        auditService.record(
                booking,
                action,
                actor,
                oldStart,
                oldEnd,
                newStart,
                newEnd,
                reason,
                informed
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
