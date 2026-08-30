package az.turn.api;

public record WalletTransactionCommandDto(
        WalletTransactionType type,
        long amount,
        WalletActorType actorType,
        Long actorUserId,
        String actorReference,
        String referenceKey,
        String description
) {
}
