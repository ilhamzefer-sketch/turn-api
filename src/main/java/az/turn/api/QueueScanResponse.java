package az.turn.api;

import java.util.List;

public record QueueScanResponse(
        long queueId,
        String address,
        String serviceName,
        List<String> categories,
        String ownerFullName,
        long queueNumber,
        long currentServingNumber,
        long waitingCount,
        long totalCustomers,
        long estimatedWaitMinutes,
        long averageServiceMinutes,
        String qrToken,
        String message
) {
}
