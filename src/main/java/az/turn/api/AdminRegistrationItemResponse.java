package az.turn.api;

import java.time.LocalDateTime;

public record AdminRegistrationItemResponse(
        long id,
        String fullName,
        String email,
        RegistrationType registrationType,
        RegistrationStatus status,
        boolean paid,
        String paymentReference,
        long paymentAmount,
        long queueCount,
        LocalDateTime createdAt
) {
}
