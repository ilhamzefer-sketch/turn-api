package az.turn.api;

import java.time.LocalDate;
import java.util.List;

public record OperationalAnalyticsDto(
        LocalDate from,
        LocalDate to,
        long totalPeople,
        long liveQueueEntries,
        long plannedBookings,
        long completed,
        long cancelled,
        long skipped,
        long removed,
        long reset,
        long guestParticipants,
        long registeredParticipants,
        long averageEstimatedWaitMinutes,
        long maximumEstimatedWaitMinutes,
        String busiestDay,
        Integer busiestHour,
        List<RoomOperationalMetricDto> rooms
) {
}
