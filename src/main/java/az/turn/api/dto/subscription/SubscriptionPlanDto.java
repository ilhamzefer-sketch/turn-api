package az.turn.api;

public record SubscriptionPlanDto(
        long id,
        String code,
        String name,
        BillingPeriod billingPeriod,
        long amount,
        String currency,
        int roomLimit,
        int employeeLimit
) {
}
