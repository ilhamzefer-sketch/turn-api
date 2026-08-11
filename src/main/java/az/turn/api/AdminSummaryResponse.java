package az.turn.api;

public record AdminSummaryResponse(
        long totalUsers,
        long totalPaidUsers,
        long totalQueues,
        long totalRevenue,
        long totalCustomers,
        long activeQueues,
        long expiredUsers,
        long pendingPayments,
        long completedPayments
) {
}
