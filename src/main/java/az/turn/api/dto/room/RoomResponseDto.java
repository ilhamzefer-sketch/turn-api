package az.turn.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record RoomResponseDto(
        long id,
        Long businessId,
        Long branchId,
        Long individualWorkspaceId,
        long createdByUserId,
        String name,
        String roomNumberOrCode,
        String description,
        String notes,
        String timezone,
        ReservationMode reservationMode,
        int defaultSlotDurationMinutes,
        int appointmentBufferMinutes,
        int bookingWindowDays,
        int minimumAdvanceMinutes,
        int cancellationCutoffMinutes,
        LiveQueueResetPolicy liveQueueResetPolicy,
        LocalTime liveQueueResetLocalTime,
        Integer liveQueueResetIntervalMinutes,
        Integer liveQueueMaxParticipants,
        boolean liveQueueAcceptingNewEntries,
        RoomStatus status,
        RoomVisibility visibility,
        String personalPublicAddress,
        BigDecimal personalLatitude,
        BigDecimal personalLongitude,
        LocalDateTime createdAt,
        LocalDateTime archivedAt
) {
}
