package az.turn.api;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionCoinPaymentSnapshotTests {
    @Test
    void capturesTheCompletePreviousSubscriptionState() {
        SubscriptionPlanEntity plan = plan();
        ProviderSubscriptionEntity subscription = subscription(plan);
        SubscriptionCoinPaymentEntity payment = new SubscriptionCoinPaymentEntity();

        payment.captureSubscriptionState(subscription, true);

        assertThat(payment.isSubscriptionStateCaptured()).isTrue();
        assertThat(payment.isSubscriptionExistedBefore()).isTrue();
        assertThat(payment.getPreviousSubscriptionPlan()).isSameAs(plan);
        assertThat(payment.getPreviousSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(payment.getPreviousBillingPeriod()).isEqualTo(BillingPeriod.MONTHLY);
        assertThat(payment.getPreviousRoomLimit()).isEqualTo(5);
        assertThat(payment.getPreviousEmployeeLimit()).isEqualTo(12);
        assertThat(payment.getPreviousExpiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 12, 0));
    }

    @Test
    void recordsThatANewSubscriptionHadNoPreviousState() {
        SubscriptionCoinPaymentEntity payment = new SubscriptionCoinPaymentEntity();

        payment.captureSubscriptionState(subscription(plan()), false);

        assertThat(payment.isSubscriptionStateCaptured()).isTrue();
        assertThat(payment.isSubscriptionExistedBefore()).isFalse();
        assertThat(payment.getPreviousSubscriptionPlan()).isNull();
        assertThat(payment.getPreviousSubscriptionStatus()).isNull();
    }

    @Test
    void cannotOverwriteTheCapturedState() {
        SubscriptionCoinPaymentEntity payment = new SubscriptionCoinPaymentEntity();
        ProviderSubscriptionEntity subscription = subscription(plan());
        payment.captureSubscriptionState(subscription, true);

        assertThatThrownBy(() -> payment.captureSubscriptionState(subscription, true))
                .isInstanceOf(IllegalStateException.class);
    }

    private SubscriptionPlanEntity plan() {
        SubscriptionPlanEntity plan = new SubscriptionPlanEntity();
        plan.setId(7L);
        plan.setCode("BUSINESS_MONTHLY");
        plan.setName("Biznes aylıq");
        plan.setBillingPeriod(BillingPeriod.MONTHLY);
        plan.setScopeType(ProviderScopeType.BUSINESS);
        plan.setCoinPrice(100L);
        plan.setRoomLimit(5);
        plan.setEmployeeLimit(500);
        plan.setActive(true);
        return plan;
    }

    private ProviderSubscriptionEntity subscription(SubscriptionPlanEntity plan) {
        ProviderSubscriptionEntity subscription = new ProviderSubscriptionEntity();
        subscription.setId(9L);
        subscription.setScopeType(ProviderScopeType.BUSINESS);
        subscription.setScopeId(12L);
        subscription.setPlan(plan);
        subscription.setBillingPeriod(BillingPeriod.MONTHLY);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setRoomLimit(5);
        subscription.setEmployeeLimit(12);
        subscription.setStartsAt(LocalDateTime.of(2026, 9, 1, 12, 0));
        subscription.setExpiresAt(LocalDateTime.of(2026, 10, 1, 12, 0));
        subscription.setGraceEndsAt(LocalDateTime.of(2026, 10, 8, 12, 0));
        subscription.setUsageGraceEndsAt(LocalDateTime.of(2026, 10, 2, 12, 0));
        return subscription;
    }
}
