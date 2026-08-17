package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OperationalAnalyticsService {
    private final ProviderAccessService accessService;
    private final RoomRepository roomRepository;
    private final LiveQueueEntryRepository liveEntryRepository;
    private final PlannedBookingRepository bookingRepository;

    public OperationalAnalyticsService(
            ProviderAccessService accessService,
            RoomRepository roomRepository,
            LiveQueueEntryRepository liveEntryRepository,
            PlannedBookingRepository bookingRepository
    ) {
        this.accessService = accessService;
        this.roomRepository = roomRepository;
        this.liveEntryRepository = liveEntryRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public OperationalAnalyticsDto business(long businessId, long userId, LocalDate from, LocalDate to) {
        accessService.requireBusinessManager(businessId, userId);
        List<RoomEntity> rooms = roomRepository.findByBranchBusinessIdOrderByCreatedAtAsc(businessId);
        return calculate(rooms, from, to);
    }

    @Transactional(readOnly = true)
    public OperationalAnalyticsDto room(long roomId, long userId, LocalDate from, LocalDate to) {
        RoomEntity room = accessService.requireRoomViewer(roomId, userId);
        return calculate(List.of(room), from, to);
    }

    private OperationalAnalyticsDto calculate(List<RoomEntity> rooms, LocalDate from, LocalDate to) {
        validateRange(from, to);
        if (rooms.isEmpty()) return empty(from, to);
        List<Long> roomIds = rooms.stream().map(RoomEntity::getId).toList();
        LocalDateTime fromTime = from.atStartOfDay();
        LocalDateTime toTime = to.plusDays(1).atStartOfDay();
        List<LiveQueueEntryEntity> live = liveEntryRepository
                .findByRoomIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(roomIds, fromTime, toTime);
        List<PlannedBookingEntity> bookings = bookingRepository
                .findByRoomIdInAndStartAtGreaterThanEqualAndStartAtLessThan(roomIds, fromTime, toTime);
        List<RoomOperationalMetricDto> roomMetrics = rooms.stream()
                .map(room -> roomMetric(room, live, bookings))
                .toList();
        long averageWait = live.isEmpty() ? 0 : Math.round(live.stream().mapToLong(this::estimatedWait).average().orElse(0));
        long maxWait = live.stream().mapToLong(this::estimatedWait).max().orElse(0);
        Map<LocalDate, Long> days = new HashMap<>();
        Map<Integer, Long> hours = new HashMap<>();
        live.forEach(entry -> countTime(entry.getCreatedAt(), days, hours));
        bookings.forEach(booking -> countTime(booking.getStartAt(), days, hours));
        LocalDate busiestDay = maxKey(days);
        return new OperationalAnalyticsDto(
                from, to, live.size() + bookings.size(), live.size(), bookings.size(),
                roomMetrics.stream().mapToLong(RoomOperationalMetricDto::completed).sum(),
                roomMetrics.stream().mapToLong(RoomOperationalMetricDto::cancelled).sum(),
                roomMetrics.stream().mapToLong(RoomOperationalMetricDto::skipped).sum(),
                roomMetrics.stream().mapToLong(RoomOperationalMetricDto::removed).sum(),
                roomMetrics.stream().mapToLong(RoomOperationalMetricDto::reset).sum(),
                roomMetrics.stream().mapToLong(RoomOperationalMetricDto::guestParticipants).sum(),
                roomMetrics.stream().mapToLong(RoomOperationalMetricDto::registeredParticipants).sum(),
                averageWait, maxWait, busiestDay == null ? null : busiestDay.toString(), maxKey(hours), roomMetrics
        );
    }

    private RoomOperationalMetricDto roomMetric(
            RoomEntity room,
            List<LiveQueueEntryEntity> live,
            List<PlannedBookingEntity> bookings
    ) {
        List<LiveQueueEntryEntity> roomLive = live.stream().filter(value -> value.getRoom().getId().equals(room.getId())).toList();
        List<PlannedBookingEntity> roomBookings = bookings.stream()
                .filter(value -> value.getRoom().getId().equals(room.getId())).toList();
        long liveCompleted = countLive(roomLive, LiveQueueEntryStatus.COMPLETED);
        long bookingCompleted = countBooking(roomBookings, PlannedBookingStatus.COMPLETED);
        long cancelled = countBooking(roomBookings, PlannedBookingStatus.CANCELLED);
        long guests = roomLive.stream().filter(value -> value.getGuestContact() != null).count()
                + roomBookings.stream().filter(value -> value.getGuestContact() != null).count();
        Long branchId = room.getBranch() == null ? null : room.getBranch().getId();
        String branchName = room.getBranch() == null ? null : room.getBranch().getName();
        return new RoomOperationalMetricDto(
                room.getId(), room.getName(), branchId, branchName, roomLive.size(), roomBookings.size(),
                liveCompleted + bookingCompleted, cancelled, countLive(roomLive, LiveQueueEntryStatus.SKIPPED),
                countLive(roomLive, LiveQueueEntryStatus.REMOVED), countLive(roomLive, LiveQueueEntryStatus.RESET),
                guests, roomLive.size() + roomBookings.size() - guests,
                (liveCompleted + bookingCompleted) * room.getDefaultSlotDurationMinutes()
        );
    }

    private long countLive(List<LiveQueueEntryEntity> values, LiveQueueEntryStatus status) {
        return values.stream().filter(value -> value.getStatus() == status).count();
    }

    private long countBooking(List<PlannedBookingEntity> values, PlannedBookingStatus status) {
        return values.stream().filter(value -> value.getStatus() == status).count();
    }

    private long estimatedWait(LiveQueueEntryEntity entry) {
        return Math.max(0, entry.getQueuePosition() - 1) * entry.getRoom().getDefaultSlotDurationMinutes();
    }

    private <T> T maxKey(Map<T, Long> values) {
        return values.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private void countTime(LocalDateTime value, Map<LocalDate, Long> days, Map<Integer, Long> hours) {
        days.merge(value.toLocalDate(), 1L, Long::sum);
        hours.merge(value.getHour(), 1L, Long::sum);
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(366))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hesabat aralığı 0-366 gün olmalıdır.");
        }
    }

    private OperationalAnalyticsDto empty(LocalDate from, LocalDate to) {
        return new OperationalAnalyticsDto(
                from, to, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, null, new ArrayList<>()
        );
    }
}
