package az.turn.api;

import java.util.List;

public record AdminDashboardResponse(
        AdminSummaryResponse summary,
        List<AdminMonthlyPaymentResponse> monthlyPayments,
        List<AdminRegistrationItemResponse> registrations,
        List<AdminPaymentItemResponse> recentPayments,
        List<AdminQueueItemResponse> queues
) {
}
