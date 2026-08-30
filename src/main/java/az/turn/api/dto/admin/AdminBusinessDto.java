package az.turn.api;

public record AdminBusinessDto(
        long id,
        String name,
        ProviderStatus status,
        long ownerUserId,
        String ownerName,
        String ownerPhone,
        long roomCount,
        Integer roomLimit,
        SubscriptionStatus subscriptionStatus
) {
}
