package az.turn.api;

import java.time.LocalDateTime;
import java.util.List;

public record QueueResponse(
        long id,
        long registrationId,
        RegistrationType registrationType,
        String fullName,
        String email,
        String address,
        String serviceName,
        List<String> categories,
        String qrToken,
        String managerUsername,
        QueueResetMode resetMode,
        LocalDateTime resetAt,
        boolean active
) {
}
