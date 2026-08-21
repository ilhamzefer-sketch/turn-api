package az.turn.api;

import java.util.List;

public record PublicRoomProfileDto(
        long id,
        String name,
        String roomNumberOrCode,
        String description,
        String timezone,
        ReservationMode reservationMode,
        int defaultSlotDurationMinutes,
        int appointmentBufferMinutes,
        boolean liveQueueAcceptingNewEntries,
        String providerName,
        String providerDescription,
        String providerLogoUrl,
        String branchName,
        PublicCategoryDto category,
        String customSubcategory,
        PublicRoomLocationDto location,
        String contactPhone,
        List<PublicRoomOwnerDto> owners,
        double averageRating,
        long ratingCount
) {
}
