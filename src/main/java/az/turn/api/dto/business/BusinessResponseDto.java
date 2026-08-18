package az.turn.api;

import java.time.LocalDateTime;

public record BusinessResponseDto(
        long id,
        long primaryOwnerUserId,
        String name,
        String legalName,
        String description,
        String taxId,
        String logoUrl,
        String phone,
        String timezone,
        ProviderStatus status,
        LocalDateTime createdAt,
        LocalDateTime archivedAt,
        PublicCategoryDto category,
        String customSubcategory
) {
}
