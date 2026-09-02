package az.turn.api;

import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
public class RoomDefaults {
    private static final int DEFAULT_SLOT_DURATION_MINUTES = 30;
    private static final LocalTime DEFAULT_LIVE_QUEUE_RESET_TIME = LocalTime.MIDNIGHT;

    public ReservationMode reservationMode(ReservationMode value) {
        return value == null ? ReservationMode.LIVE_QUEUE : value;
    }

    public int slotDurationMinutes(Integer value) {
        return value == null ? DEFAULT_SLOT_DURATION_MINUTES : value;
    }

    public RoomVisibility visibility(RoomVisibility value) {
        return value == null ? RoomVisibility.PUBLIC : value;
    }

    public void applyCreationConfiguration(RoomEntity room) {
        room.setAppointmentBufferMinutes(0);
        room.setBookingWindowDays(30);
        room.setMinimumAdvanceMinutes(30);
        room.setCancellationCutoffMinutes(0);
        room.setLiveQueueAcceptingNewEntries(true);
        normalizeModeConfiguration(room);
    }

    public void normalizeModeConfiguration(RoomEntity room) {
        if (room.getReservationMode() == ReservationMode.PLANNED_BOOKING) {
            room.setLiveQueueResetPolicy(null);
            room.setLiveQueueResetLocalTime(null);
            room.setLiveQueueResetIntervalMinutes(null);
            room.setLiveQueueMaxParticipants(null);
            return;
        }
        if (room.getLiveQueueResetPolicy() == null) {
            room.setLiveQueueResetPolicy(LiveQueueResetPolicy.DAILY_AT_TIME);
        }
        if (room.getLiveQueueResetPolicy() == LiveQueueResetPolicy.DAILY_AT_TIME) {
            if (room.getLiveQueueResetLocalTime() == null) {
                room.setLiveQueueResetLocalTime(DEFAULT_LIVE_QUEUE_RESET_TIME);
            }
            room.setLiveQueueResetIntervalMinutes(null);
            return;
        }
        room.setLiveQueueResetLocalTime(null);
    }

    public void ensureLiveQueueResetConfiguration(RoomEntity room) {
        normalizeModeConfiguration(room);
        if (room.getReservationMode() != ReservationMode.LIVE_QUEUE) return;
        if (room.getLiveQueueResetPolicy() == LiveQueueResetPolicy.DAILY_AT_TIME
                && room.getLiveQueueResetLocalTime() != null) {
            return;
        }
        if (room.getLiveQueueResetPolicy() == LiveQueueResetPolicy.EVERY_INTERVAL
                && room.getLiveQueueResetIntervalMinutes() != null
                && room.getLiveQueueResetIntervalMinutes() > 0) {
            return;
        }
        room.setLiveQueueResetPolicy(LiveQueueResetPolicy.DAILY_AT_TIME);
        room.setLiveQueueResetLocalTime(DEFAULT_LIVE_QUEUE_RESET_TIME);
        room.setLiveQueueResetIntervalMinutes(null);
    }
}
