package az.turn.api;

import java.time.LocalDateTime;
import java.util.List;

public record UserQueueHistoryItemDto(
        long entryId,
        String entrySource,
        long queueId,
        String queueName,
        String serviceName,
        String address,
        List<String> categories,
        long queueNumber,
        long currentServingNumber,
        long waitingAhead,
        long averageServiceMinutes,
        Integer rating,
        String ratingNote,
        LocalDateTime joinedAt
) {
}
