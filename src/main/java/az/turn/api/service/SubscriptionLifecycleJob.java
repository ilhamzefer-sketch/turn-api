package az.turn.api;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class SubscriptionLifecycleJob {
    private final ProviderSubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public SubscriptionLifecycleJob(ProviderSubscriptionRepository subscriptionRepository, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.subscription.lifecycle-delay-ms:300000}")
    @Transactional
    public void updateExpiredSubscriptions() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ProviderSubscriptionEntity> subscriptions = subscriptionRepository.findByStatusInAndExpiresAtBefore(
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.GRACE_PERIOD),
                now
        );
        for (ProviderSubscriptionEntity subscription : subscriptions) {
            boolean inGrace = subscription.getGraceEndsAt() != null && now.isBefore(subscription.getGraceEndsAt());
            subscription.setStatus(inGrace ? SubscriptionStatus.GRACE_PERIOD : SubscriptionStatus.SUSPENDED);
        }
        subscriptionRepository.saveAll(subscriptions);
    }
}
