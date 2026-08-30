package az.turn.api;

public record SubscriptionPlanDto(
        long id,
        String code,
        String name,
        BillingPeriod billingPeriod,
        long amount,
        String currency,
        ProviderScopeType scopeType,
        long coinPrice,
        int roomLimit,
        int employeeLimit
) {
}
