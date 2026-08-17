package az.turn.api;

public record RoomRatingSummaryDto(
        long roomId,
        double averageScore,
        long ratingCount
) {
}
