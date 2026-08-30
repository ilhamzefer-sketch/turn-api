package az.turn.api;

import java.time.LocalDateTime;

public record SubscriptionCoinPurchaseDto(
        long paymentId,
        long walletTransactionId,
        long coinsSpent,
        long balanceAfter,
        String paymentReference,
        ProviderSubscriptionDto subscription,
        LocalDateTime completedAt
) {
}
