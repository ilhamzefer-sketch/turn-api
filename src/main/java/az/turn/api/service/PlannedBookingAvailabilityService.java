package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PlannedBookingAvailabilityService {
    private final RoomRepository roomRepository;
    private final PlannedBookingRepository bookingRepository;
    private final RoomAvailabilityService roomAvailabilityService;
    private final Clock clock;

    public PlannedBookingAvailabilityService(
            RoomRepository roomRepository,
            PlannedBookingRepository bookingRepository,
            RoomAvailabilityService roomAvailabilityService,
            Clock clock
    ) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.roomAvailabilityService = roomAvailabilityService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotDto> getPublicSlots(long roomId, LocalDate date) {
        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq tapılmadı."));
        requirePublicPlannedRoom(room);
        return availableSlots(room, date, true, null).stream()
                .map(range -> new AvailableSlotDto(range.startAt(), range.endAt(), room.getTimezone()))
                .toList();
    }

    public BookingTimeRange requireAvailable(
            RoomEntity room,
            LocalDateTime requestedStart,
            boolean customerRules,
            Long excludedBookingId
    ) {
        requireOperationalPlannedRoom(room);
        return availableSlots(room, requestedStart.toLocalDate(), customerRules, excludedBookingId).stream()
                .filter(range -> range.startAt().equals(requestedStart))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Seçilmiş rezervasiya saatı artıq boş deyil və ya otağın iş cədvəlinə uyğun gəlmir."
                ));
    }

    public void requirePublicPlannedRoom(RoomEntity room) {
        requireOperationalPlannedRoom(room);
        if (room.getVisibility() == RoomVisibility.PRIVATE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq tapılmadı.");
        }
    }

    private List<BookingTimeRange> availableSlots(
            RoomEntity room,
            LocalDate date,
            boolean customerRules,
            Long excludedBookingId
    ) {
        ZonedDateTime roomNow = ZonedDateTime.ofInstant(clock.instant(), ZoneId.of(room.getTimezone()));
        if (date.isBefore(roomNow.toLocalDate())
                || date.isAfter(roomNow.toLocalDate().plusDays(room.getBookingWindowDays()))) {
            return List.of();
        }
        LocalDateTime earliest = customerRules
                ? roomNow.toLocalDateTime().plusMinutes(room.getMinimumAdvanceMinutes())
                : roomNow.toLocalDateTime();
        List<PlannedBookingEntity> occupied = bookingRepository.findOverlapping(
                room.getId(),
                PlannedBookingStatus.ACTIVE,
                date.atStartOfDay(),
                date.plusDays(2).atStartOfDay()
        ).stream().filter(booking -> !booking.getId().equals(excludedBookingId)).toList();
        List<BookingTimeRange> slots = new ArrayList<>();
        for (AvailabilityInterval interval : roomAvailabilityService.intervals(room, date)) {
            addIntervalSlots(room, date, interval, earliest, occupied, slots);
        }
        return slots;
    }

    private void addIntervalSlots(
            RoomEntity room,
            LocalDate date,
            AvailabilityInterval interval,
            LocalDateTime earliest,
            List<PlannedBookingEntity> occupied,
            List<BookingTimeRange> slots
    ) {
        LocalDateTime cursor = LocalDateTime.of(date, interval.start());
        LocalDateTime intervalEnd = LocalDateTime.of(date, interval.end());
        while (true) {
            LocalDateTime endAt = cursor.plusMinutes(room.getDefaultSlotDurationMinutes());
            LocalDateTime blockingEndAt = endAt.plusMinutes(room.getAppointmentBufferMinutes());
            if (blockingEndAt.isAfter(intervalEnd)) return;
            BookingTimeRange range = new BookingTimeRange(cursor, endAt, blockingEndAt);
            if (!cursor.isBefore(earliest) && occupied.stream().noneMatch(value -> overlaps(range, value))) {
                slots.add(range);
            }
            cursor = blockingEndAt;
        }
    }

    private boolean overlaps(BookingTimeRange range, PlannedBookingEntity booking) {
        return range.startAt().isBefore(booking.getBlockingEndAt())
                && booking.getStartAt().isBefore(range.blockingEndAt());
    }

    private void requireOperationalPlannedRoom(RoomEntity room) {
        if (room.getStatus() != RoomStatus.PUBLISHED
                || room.getReservationMode() != ReservationMode.PLANNED_BOOKING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Otaq planlı rezervasiya qəbul etmir.");
        }
    }
}
