package az.turn.api;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RoomAvailabilityService {
    private final WeeklyAvailabilityRuleRepository weeklyRepository;
    private final AvailabilityExceptionRepository exceptionRepository;

    public RoomAvailabilityService(
            WeeklyAvailabilityRuleRepository weeklyRepository,
            AvailabilityExceptionRepository exceptionRepository
    ) {
        this.weeklyRepository = weeklyRepository;
        this.exceptionRepository = exceptionRepository;
    }

    public List<AvailabilityInterval> intervals(RoomEntity room, LocalDate date) {
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
}
