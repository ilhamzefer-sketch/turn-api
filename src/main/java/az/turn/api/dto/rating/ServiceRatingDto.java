package az.turn.api;

import java.time.LocalDateTime;

public record ServiceRatingDto(
        long id,
        long roomId,
        String targetType,
        long targetId,
        int score,
        String comment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime editableUntil
) {
}
