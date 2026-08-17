package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountOwnershipDisputeRepository extends JpaRepository<AccountOwnershipDisputeEntity, Long> {
    List<AccountOwnershipDisputeEntity> findAllByOrderByCreatedAtDesc();
    long countByStatusIn(List<SupportRequestStatus> statuses);
}
