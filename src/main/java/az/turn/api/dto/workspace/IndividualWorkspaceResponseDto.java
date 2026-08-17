package az.turn.api;

import java.time.LocalDateTime;

public record IndividualWorkspaceResponseDto(
        long id,
        long ownerUserId,
        String name,
        String timezone,
        ProviderStatus status,
        LocalDateTime createdAt,
        LocalDateTime archivedAt
) {
}
