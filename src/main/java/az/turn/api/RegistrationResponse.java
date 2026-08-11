package az.turn.api;

import java.time.LocalDateTime;

public record RegistrationResponse(
        long id,
        String firstName,
        String lastName,
        String email,
        boolean paid,
        String paymentReference,
        RegistrationType registrationType,
        RegistrationStatus status,
        LocalDateTime createdAt,
        String accessToken
) {
}
