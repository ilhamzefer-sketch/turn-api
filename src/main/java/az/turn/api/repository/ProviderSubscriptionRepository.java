package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface ProviderSubscriptionRepository extends JpaRepository<ProviderSubscriptionEntity, Long> {
    Optional<ProviderSubscriptionEntity> findByScopeTypeAndScopeId(ProviderScopeType scopeType, Long scopeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from ProviderSubscriptionEntity subscription "
            + "where subscription.scopeType = :scopeType and subscription.scopeId = :scopeId")
    Optional<ProviderSubscriptionEntity> findByScopeTypeAndScopeIdForUpdate(
            ProviderScopeType scopeType,
            Long scopeId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from ProviderSubscriptionEntity subscription where subscription.id = :id")
    Optional<ProviderSubscriptionEntity> findByIdForUpdate(Long id);

    List<ProviderSubscriptionEntity> findByStatusInAndExpiresAtBefore(
            List<SubscriptionStatus> statuses,
            LocalDateTime expiresAt
    );
    long countByStatus(SubscriptionStatus status);

    List<ProviderSubscriptionEntity> findByScopeTypeAndScopeIdIn(
            ProviderScopeType scopeType,
            Collection<Long> scopeIds
    );
}
