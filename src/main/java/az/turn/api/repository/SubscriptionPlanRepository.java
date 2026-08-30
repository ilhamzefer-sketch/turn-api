package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, Long> {
    List<SubscriptionPlanEntity> findByActiveTrueOrderByCoinPriceAsc();
    List<SubscriptionPlanEntity> findByActiveTrueAndScopeTypeOrderByCoinPriceAsc(ProviderScopeType scopeType);
    Optional<SubscriptionPlanEntity> findByCodeAndActiveTrue(String code);
}
