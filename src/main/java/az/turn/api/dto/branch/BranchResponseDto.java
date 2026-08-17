package az.turn.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BranchResponseDto(
        long id,
        long businessId,
        String name,
        String address,
        String city,
        String district,
        BigDecimal latitude,
        BigDecimal longitude,
        String phone,
        String effectivePhone,
        String notes,
        String timezone,
        ProviderStatus status,
        LocalDateTime createdAt,
        LocalDateTime archivedAt
) {
}
