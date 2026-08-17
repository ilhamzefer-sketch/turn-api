package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessOwnershipTransferRepository extends JpaRepository<BusinessOwnershipTransferEntity, Long> {
    boolean existsByBusinessIdAndStatus(Long businessId, OwnershipTransferStatus status);
    List<BusinessOwnershipTransferEntity> findByToAdminUserIdAndStatusOrderByCreatedAtDesc(
            Long userId,
            OwnershipTransferStatus status
    );
    Optional<BusinessOwnershipTransferEntity> findByIdAndToAdminUserId(Long id, Long userId);
}
