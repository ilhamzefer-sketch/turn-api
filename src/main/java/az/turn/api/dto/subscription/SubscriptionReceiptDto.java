package az.turn.api;

import java.time.LocalDateTime;

public record SubscriptionReceiptDto(
        long paymentId,
        String planCode,
        BillingPeriod billingPeriod,
        PaymentStatus status,
        long amount,
        String currency,
        String provider,
        String paymentReference,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
