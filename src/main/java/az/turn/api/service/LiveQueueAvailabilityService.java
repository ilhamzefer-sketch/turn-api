package az.turn.api;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LiveQueueAvailabilityService {
    private static final int OPENING_SEARCH_DAYS = 14;

    private final WeeklyAvailabilityRuleRepository weeklyRepository;
    private final AvailabilityExceptionRepository exceptionRepository;
    private final Clock clock;

    public LiveQueueAvailabilityService(
            WeeklyAvailabilityRuleRepository weeklyRepository,
            AvailabilityExceptionRepository exceptionRepository,
            Clock clock
    ) {
        this.weeklyRepository = weeklyRepository;
        this.exceptionRepository = exceptionRepository;
        this.clock = clock;
    }

    public boolean isAvailableNow(RoomEntity room) {
        ZonedDateTime roomNow = ZonedDateTime.ofInstant(clock.instant(), ZoneId.of(room.getTimezone()));
        return intervals(room, roomNow.toLocalDate()).stream()
                .anyMatch(interval -> contains(interval, roomNow.toLocalTime()));
    }

    public LocalDateTime nextOpeningAt(RoomEntity room) {
        ZoneId roomZone = ZoneId.of(room.getTimezone());
        ZonedDateTime roomNow = ZonedDateTime.ofInstant(clock.instant(), roomZone);
        for (int dayOffset = 0; dayOffset <= OPENING_SEARCH_DAYS; dayOffset++) {
            LocalDate date = roomNow.toLocalDate().plusDays(dayOffset);
            for (AvailabilityInterval interval : intervals(room, date)) {
                LocalDateTime candidate = LocalDateTime.of(date, interval.start());
                if (dayOffset > 0 || candidate.isAfter(roomNow.toLocalDateTime())) {
                    return LocalDateTime.ofInstant(candidate.atZone(roomZone).toInstant(), clock.getZone());
                }
            }
        }
        return null;
    }

    private List<AvailabilityInterval> intervals(RoomEntity room, LocalDate date) {
        List<AvailabilityExceptionEntity> exceptions = exceptionRepository
                .findByRoomIdAndDateOrderByStartTimeAsc(room.getId(), date);
        if (exceptions.stream().anyMatch(value -> value.getType() == AvailabilityExceptionType.CLOSED)) {
            return List.of();
        }

        List<AvailabilityInterval> base = exceptions.stream()
                .filter(value -> value.getType() == AvailabilityExceptionType.CUSTOM_HOURS)
                .map(value -> new AvailabilityInterval(value.getStartTime(), value.getEndTime()))
                .toList();
        if (base.isEmpty()) {
            base = weeklyRepository.findByRoomIdAndDayOfWeekOrderByStartTimeAsc(room.getId(), date.getDayOfWeek())
                    .stream()
                    .filter(WeeklyAvailabilityRuleEntity::isActive)
                    .map(value -> new AvailabilityInterval(value.getStartTime(), value.getEndTime()))
                    .toList();
        }

        List<AvailabilityInterval> blocked = exceptions.stream()
                .filter(value -> value.getType() == AvailabilityExceptionType.BLOCKED_INTERVAL)
                .map(value -> new AvailabilityInterval(value.getStartTime(), value.getEndTime()))
                .toList();
        return base.stream()
                .flatMap(interval -> subtract(interval, blocked).stream())
                .sorted(Comparator.comparing(AvailabilityInterval::start))
                .toList();
    }

    private List<AvailabilityInterval> subtract(
            AvailabilityInterval interval,
            List<AvailabilityInterval> blocked
    ) {
        List<AvailabilityInterval> result = new ArrayList<>();
        result.add(interval);
        for (AvailabilityInterval block : blocked) {
            List<AvailabilityInterval> next = new ArrayList<>();
            for (AvailabilityInterval current : result) next.addAll(subtractOne(current, block));
            result = next;
        }
        return result;
    }

    private List<AvailabilityInterval> subtractOne(AvailabilityInterval value, AvailabilityInterval block) {
        if (!block.start().isBefore(value.end()) || !block.end().isAfter(value.start())) return List.of(value);
        List<AvailabilityInterval> result = new ArrayList<>();
        if (block.start().isAfter(value.start())) result.add(new AvailabilityInterval(value.start(), block.start()));
        if (block.end().isBefore(value.end())) result.add(new AvailabilityInterval(block.end(), value.end()));
        return result;
    }

    private boolean contains(AvailabilityInterval interval, LocalTime value) {
        return !value.isBefore(interval.start()) && value.isBefore(interval.end());
    }
}
