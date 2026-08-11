package az.turn.api;

import java.time.LocalDateTime;

public record AdminPaymentItemResponse(
        long id,
        String customerName,
        String email,
        RegistrationType registrationType,
        PaymentStatus status,
        long amount,
        String currency,
        String provider,
        String externalOrderId,
        String paymentReference,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
