package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface BusinessRepository extends JpaRepository<BusinessEntity, Long> {
    boolean existsByPrimaryOwnerUserIdAndStatus(Long userId, ProviderStatus status);
    long countByStatusNot(ProviderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select business from BusinessEntity business where business.id = :id")
    Optional<BusinessEntity> findByIdForUpdate(Long id);
}
