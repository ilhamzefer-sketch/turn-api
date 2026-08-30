package az.turn.api;

import java.time.LocalDateTime;

public record WalletTransactionDto(
        long id,
        WalletTransactionType type,
        WalletTransactionDirection direction,
        long amount,
        long balanceBefore,
        long balanceAfter,
        WalletActorType actorType,
        String referenceKey,
        String description,
        LocalDateTime createdAt
) {
}
