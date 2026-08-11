package az.turn.api;

import java.time.LocalDateTime;
import java.util.List;

public record QueueDetailResponse(
        long id,
        long registrationId,
        RegistrationType registrationType,
        String ownerFullName,
        String ownerEmail,
        String address,
        String serviceName,
        List<String> categories,
        String qrToken,
        long currentServingNumber,
        long lastIssuedNumber,
        long waitingCount,
        long totalCustomers,
        long averageServiceMinutes,
        long estimatedWaitMinutes,
        LocalDateTime lastAdvancedAt,
        String managerUsername,
        QueueResetMode resetMode,
        LocalDateTime resetAt,
        boolean active
) {
}
