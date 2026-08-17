package az.turn.api;

import java.time.LocalDateTime;

public record ProviderSubscriptionDto(
        long id,
        ProviderScopeType scopeType,
        long scopeId,
        SubscriptionPlanDto plan,
        BillingPeriod billingPeriod,
        SubscriptionStatus status,
        int roomLimit,
        int employeeLimit,
        LocalDateTime startsAt,
        LocalDateTime expiresAt,
        LocalDateTime graceEndsAt,
        LocalDateTime usageGraceEndsAt
) {
}
