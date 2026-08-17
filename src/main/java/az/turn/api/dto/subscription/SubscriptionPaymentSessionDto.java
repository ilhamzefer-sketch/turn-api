package az.turn.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

public record SubscriptionPaymentSessionDto(
        long id,
        @JsonIgnore String sessionToken,
        PaymentStatus status,
        String provider,
        String paymentMode,
        long amount,
        String currency,
        String paymentReference,
        String checkoutUrl,
        ProviderSubscriptionDto subscription,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
