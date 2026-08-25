package az.turn.api;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class LiveQueueAvailabilityService {
    private static final int OPENING_SEARCH_DAYS = 14;

    private final RoomAvailabilityService roomAvailabilityService;
    private final Clock clock;

    public LiveQueueAvailabilityService(
            RoomAvailabilityService roomAvailabilityService,
            Clock clock
    ) {
        this.roomAvailabilityService = roomAvailabilityService;
        this.clock = clock;
    }

    public boolean isAvailableNow(RoomEntity room) {
        ZonedDateTime roomNow = ZonedDateTime.ofInstant(clock.instant(), ZoneId.of(room.getTimezone()));
        return roomAvailabilityService.intervals(room, roomNow.toLocalDate()).stream()
                .anyMatch(interval -> contains(interval, roomNow.toLocalTime()));
    }

    public OffsetDateTime nextOpeningAt(RoomEntity room) {
        ZoneId roomZone = ZoneId.of(room.getTimezone());
        ZonedDateTime roomNow = ZonedDateTime.ofInstant(clock.instant(), roomZone);
        for (int dayOffset = 0; dayOffset <= OPENING_SEARCH_DAYS; dayOffset++) {
            LocalDate date = roomNow.toLocalDate().plusDays(dayOffset);
            for (AvailabilityInterval interval : roomAvailabilityService.intervals(room, date)) {
                LocalDateTime candidate = LocalDateTime.of(date, interval.start());
                if (dayOffset > 0 || candidate.isAfter(roomNow.toLocalDateTime())) {
                    return candidate.atZone(roomZone).toOffsetDateTime();
                }
            }
        }
        return null;
    }

    private boolean contains(AvailabilityInterval interval, LocalTime value) {
        return !value.isBefore(interval.start()) && value.isBefore(interval.end());
    }
}
