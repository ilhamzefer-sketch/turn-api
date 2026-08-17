package az.turn.api;

public record RoomOperationalMetricDto(
        long roomId,
        String roomName,
        Long branchId,
        String branchName,
        long liveEntries,
        long plannedBookings,
        long completed,
        long cancelled,
        long skipped,
        long removed,
        long reset,
        long guestParticipants,
        long registeredParticipants,
        long estimatedCapacityMinutes
) {
}
