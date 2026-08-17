package az.turn.api;

import org.springframework.stereotype.Component;

@Component
public class SubscriptionMapper {
    public SubscriptionPlanDto toDto(SubscriptionPlanEntity plan) {
        return new SubscriptionPlanDto(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getBillingPeriod(),
                plan.getAmount(),
                plan.getCurrency(),
                plan.getRoomLimit(),
                plan.getEmployeeLimit()
        );
    }

    public ProviderSubscriptionDto toDto(ProviderSubscriptionEntity subscription) {
        return new ProviderSubscriptionDto(
                subscription.getId(),
                subscription.getScopeType(),
                subscription.getScopeId(),
                toDto(subscription.getPlan()),
                subscription.getBillingPeriod(),
                subscription.getStatus(),
                subscription.getRoomLimit(),
                subscription.getEmployeeLimit(),
                subscription.getStartsAt(),
                subscription.getExpiresAt(),
                subscription.getGraceEndsAt(),
                subscription.getUsageGraceEndsAt()
        );
    }
}
