package az.turn.api;

import java.util.List;

public record PublicRoomSummaryDto(
        long id,
        String name,
        String description,
        ReservationMode reservationMode,
        String providerName,
        String branchName,
        PublicCategoryDto category,
        String customSubcategory,
        List<String> serviceNames,
        PublicRoomLocationDto location,
        double averageRating,
        long ratingCount
) {
}
