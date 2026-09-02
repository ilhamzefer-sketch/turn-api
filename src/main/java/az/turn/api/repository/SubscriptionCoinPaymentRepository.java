package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionCoinPaymentRepository extends JpaRepository<SubscriptionCoinPaymentEntity, Long> {
    long countByStatus(PaymentStatus status);

    Optional<SubscriptionCoinPaymentEntity> findByPayerUserIdAndIdempotencyKey(long userId, String idempotencyKey);

    List<SubscriptionCoinPaymentEntity> findByProviderSubscriptionIdOrderByCreatedAtDescIdDesc(long subscriptionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from SubscriptionCoinPaymentEntity payment "
            + "where payment.payerUser.id = :userId and payment.status = :status "
            + "and payment.createdAt >= :createdAt order by payment.createdAt desc, payment.id desc")
    List<SubscriptionCoinPaymentEntity> findForFraudRecovery(
            long userId,
            PaymentStatus status,
            LocalDateTime createdAt
    );
}
