package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class SubscriptionActivationService {
    private final ProviderSubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public SubscriptionActivationService(ProviderSubscriptionRepository subscriptionRepository, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Transactional
    public ProviderSubscriptionEntity activate(long subscriptionId, SubscriptionPlanEntity plan) {
        ProviderSubscriptionEntity subscription = subscriptionRepository.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Abunəlik tapılmadı."));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime base = subscription.getExpiresAt() != null && subscription.getExpiresAt().isAfter(now)
                ? subscription.getExpiresAt()
                : now;
        LocalDateTime expiresAt = plan.getBillingPeriod() == BillingPeriod.YEARLY
                ? base.plusYears(1)
                : base.plusMonths(1);
        subscription.setPlan(plan);
        subscription.setBillingPeriod(plan.getBillingPeriod());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setRoomLimit(Math.max(subscription.getRoomLimit(), plan.getRoomLimit()));
        subscription.setEmployeeLimit(Math.max(subscription.getEmployeeLimit(), plan.getEmployeeLimit()));
        if (subscription.getStartsAt() == null) subscription.setStartsAt(now);
        subscription.setExpiresAt(expiresAt);
        subscription.setGraceEndsAt(expiresAt.plusDays(7));
        return subscriptionRepository.save(subscription);
    }
}
