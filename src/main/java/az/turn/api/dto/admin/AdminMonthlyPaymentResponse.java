package az.turn.api;

public record AdminMonthlyPaymentResponse(
        String month,
        long registrations,
        long revenueAmount
) {
}
