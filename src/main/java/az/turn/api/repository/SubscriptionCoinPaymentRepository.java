package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionCoinPaymentRepository extends JpaRepository<SubscriptionCoinPaymentEntity, Long> {
    long countByStatus(PaymentStatus status);

    Optional<SubscriptionCoinPaymentEntity> findByPayerUserIdAndIdempotencyKey(long userId, String idempotencyKey);

    List<SubscriptionCoinPaymentEntity> findByProviderSubscriptionIdOrderByCreatedAtDescIdDesc(long subscriptionId);
}
