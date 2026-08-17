package az.turn.api;

public record AdminPlatformOverviewDto(
        long users,
        long activeUsers,
        long suspendedUsers,
        long businesses,
        long rooms,
        long activeSubscriptions,
        long graceSubscriptions,
        long suspendedSubscriptions,
        long completedSubscriptionPayments,
        long openOwnershipDisputes,
        long openPhoneChanges,
        long openDeletionRequests
) {
}
