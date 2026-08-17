package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class PlannedBookingCommandSupport {
    private final RoomRepository roomRepository;
    private final PlannedBookingRepository bookingRepository;
    private final RoomServiceItemRepository serviceRepository;
    private final SecureTokenService tokenService;
    private final Clock clock;

    public PlannedBookingCommandSupport(
            RoomRepository roomRepository,
            PlannedBookingRepository bookingRepository,
            RoomServiceItemRepository serviceRepository,
            SecureTokenService tokenService,
            Clock clock
    ) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.serviceRepository = serviceRepository;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    public RoomEntity lockRoom(long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq tapılmadı."));
    }

    public PlannedBookingEntity requireBooking(long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rezervasiya tapılmadı."));
    }

    public long requireBookingRoomId(long bookingId) {
        return bookingRepository.findRoomIdById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rezervasiya tapılmadı."));
    }

    public PlannedBookingEntity requireBookingForUpdate(long bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rezervasiya tapılmadı."));
    }

    public PlannedBookingEntity requireRoomBooking(long roomId, long bookingId) {
        return bookingRepository.findByIdAndRoomId(bookingId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rezervasiya tapılmadı."));
    }

    public PlannedBookingEntity requireRoomBookingForUpdate(long roomId, long bookingId) {
        return bookingRepository.findByIdAndRoomIdForUpdate(bookingId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rezervasiya tapılmadı."));
    }

    public RoomServiceItemEntity resolveService(RoomEntity room, Long serviceId) {
        if (serviceId == null) return null;
        RoomServiceItemEntity service = serviceRepository.findByIdAndRoomId(serviceId, room.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq xidməti tapılmadı."));
        if (!service.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seçilmiş xidmət aktiv deyil.");
        }
        return service;
    }

    public PlannedBookingEntity baseBooking(
            RoomEntity room,
            BookingTimeRange range,
            RoomServiceItemEntity service,
            LiveQueueEntrySource source,
            UserEntity creator
    ) {
        PlannedBookingEntity booking = new PlannedBookingEntity();
        booking.setRoom(room);
        booking.setRoomService(service);
        booking.setBookingReference("B-" + tokenService.generate().substring(0, 12).toUpperCase());
        booking.setStatus(PlannedBookingStatus.ACTIVE);
        booking.setSource(source);
        booking.setCreatedByUser(creator);
        booking.setStartAt(range.startAt());
        booking.setEndAt(range.endAt());
        booking.setBlockingEndAt(range.blockingEndAt());
        booking.setActiveSlot(1);
        return booking;
    }

    public void applyRange(PlannedBookingEntity booking, BookingTimeRange range) {
        booking.setStartAt(range.startAt());
        booking.setEndAt(range.endAt());
        booking.setBlockingEndAt(range.blockingEndAt());
        booking.setActiveSlot(1);
        booking.setActiveCustomerSlot(booking.getUser() == null ? null : 1);
    }

    public void cancel(
            PlannedBookingEntity booking,
            BookingCancellationReason reason,
            String detail
    ) {
        requireActive(booking);
        booking.setStatus(PlannedBookingStatus.CANCELLED);
        booking.setCancellationReason(reason);
        booking.setCancellationDetail(normalizeOptional(detail));
        booking.setCancelledAt(LocalDateTime.now(clock));
        booking.setActiveSlot(null);
        booking.setActiveCustomerSlot(null);
    }

    public void complete(PlannedBookingEntity booking) {
        requireActive(booking);
        booking.setStatus(PlannedBookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now(clock));
        booking.setActiveSlot(null);
        booking.setActiveCustomerSlot(null);
    }

    public PlannedBookingEntity save(PlannedBookingEntity booking) {
        return bookingRepository.save(booking);
    }

    public void requireActive(PlannedBookingEntity booking) {
        if (booking.getStatus() != PlannedBookingStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yalnız aktiv rezervasiya dəyişdirilə bilər.");
        }
    }

    public String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
