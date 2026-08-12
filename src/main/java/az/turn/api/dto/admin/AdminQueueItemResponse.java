package az.turn.api;

import java.time.LocalDateTime;

public record AdminQueueItemResponse(
        long id,
        String serviceName,
        String address,
        String ownerFullName,
        RegistrationType registrationType,
        long currentServingNumber,
        long lastIssuedNumber,
        long waitingCount,
        long averageServiceMinutes,
        QueueResetMode resetMode,
        LocalDateTime resetAt,
        boolean active
) {
}
