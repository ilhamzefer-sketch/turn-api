package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;

public interface PaymentSessionRepository extends JpaRepository<PaymentSessionEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from PaymentSessionEntity payment where payment.id = :id")
    Optional<PaymentSessionEntity> findByIdForUpdate(long id);

    List<PaymentSessionEntity> findByPaymentPurposeAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            PaymentPurpose purpose, PaymentStatus status, LocalDateTime createdBefore, Pageable pageable
    );
    long countByPaymentPurposeAndStatus(PaymentPurpose purpose, PaymentStatus status);
    List<PaymentSessionEntity> findByProviderSubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
}
