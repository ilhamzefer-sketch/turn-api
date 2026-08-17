package az.turn.api;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class LiveQueueSessionFactory {
    private final Clock clock;

    public LiveQueueSessionFactory(Clock clock) {
        this.clock = clock;
    }

    public LiveQueueSessionEntity create(RoomEntity room, LiveQueueAcceptanceOverride acceptanceOverride) {
        LocalDateTime now = LocalDateTime.now(clock);
        ZonedDateTime roomNow = ZonedDateTime.ofInstant(clock.instant(), ZoneId.of(room.getTimezone()));
        LiveQueueSessionEntity session = new LiveQueueSessionEntity();
        session.setRoom(room);
        session.setServiceDate(roomNow.toLocalDate());
        session.setStatus(LiveQueueSessionStatus.OPEN);
        session.setOpenSlot(1);
        session.setAcceptanceOverride(acceptanceOverride);
        session.setResetPolicy(room.getLiveQueueResetPolicy());
        session.setResetLocalTime(room.getLiveQueueResetLocalTime());
        session.setResetIntervalMinutes(room.getLiveQueueResetIntervalMinutes());
        session.setNextResetAt(nextResetAt(room, roomNow));
        session.setNextPosition(0);
        session.setOpenedAt(now);
        return session;
    }

    private LocalDateTime nextResetAt(RoomEntity room, ZonedDateTime roomNow) {
        ZonedDateTime reset;
        if (room.getLiveQueueResetPolicy() == LiveQueueResetPolicy.DAILY_AT_TIME) {
            reset = roomNow.toLocalDate().atTime(room.getLiveQueueResetLocalTime()).atZone(roomNow.getZone());
            if (!reset.isAfter(roomNow)) reset = reset.plusDays(1);
        } else {
            reset = roomNow.plusMinutes(room.getLiveQueueResetIntervalMinutes());
        }
        return LocalDateTime.ofInstant(reset.toInstant(), clock.getZone());
    }
}
