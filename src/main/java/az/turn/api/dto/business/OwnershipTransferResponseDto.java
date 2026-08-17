package az.turn.api;

import java.time.LocalDateTime;

public record OwnershipTransferResponseDto(
        long id,
        long businessId,
        long fromOwnerUserId,
        long toAdminUserId,
        OwnershipTransferStatus status,
        LocalDateTime createdAt,
        LocalDateTime respondedAt
) {
}
