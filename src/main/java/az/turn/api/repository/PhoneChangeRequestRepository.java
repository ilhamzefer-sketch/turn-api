package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhoneChangeRequestRepository extends JpaRepository<PhoneChangeRequestEntity, Long> {
    boolean existsByUserIdAndStatusIn(Long userId, List<SupportRequestStatus> statuses);
    List<PhoneChangeRequestEntity> findAllByOrderByCreatedAtDesc();
    long countByStatusIn(List<SupportRequestStatus> statuses);
}
