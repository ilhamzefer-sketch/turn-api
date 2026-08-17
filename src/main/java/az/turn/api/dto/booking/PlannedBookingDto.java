package az.turn.api;

import java.time.LocalDateTime;

public record PlannedBookingDto(
        Long id,
        String bookingReference,
        Long roomId,
        String roomName,
        Long serviceId,
        String serviceName,
        PlannedBookingStatus status,
        String participantName,
        String participantPhone,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String timezone,
        String customerNote,
        String internalNote,
        LiveQueueEntrySource source,
        BookingCancellationReason cancellationReason,
        String cancellationDetail,
        Long createdByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt
) {
}
