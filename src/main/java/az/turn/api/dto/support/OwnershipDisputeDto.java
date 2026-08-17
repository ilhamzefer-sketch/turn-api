package az.turn.api;

import java.time.LocalDateTime;

public record OwnershipDisputeDto(
        long id,
        Long disputedUserId,
        String disputedPhone,
        String claimantName,
        String claimantContactPhone,
        String description,
        SupportRequestStatus status,
        DisputeResolutionAction resolutionAction,
        String resolutionNote,
        String reviewedByAdmin,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
}
