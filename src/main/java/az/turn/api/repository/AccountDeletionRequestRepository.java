package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequestEntity, Long> {
    boolean existsByUserIdAndStatusIn(Long userId, List<SupportRequestStatus> statuses);
    List<AccountDeletionRequestEntity> findAllByOrderByRequestedAtDesc();
    long countByStatusIn(List<SupportRequestStatus> statuses);
}
