package az.turn.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        RoomStatus status,
        RoomVisibility visibility,
        String personalPublicAddress,
        BigDecimal personalLatitude,
        BigDecimal personalLongitude,
        LocalDateTime createdAt,
        LocalDateTime archivedAt
) {
}
