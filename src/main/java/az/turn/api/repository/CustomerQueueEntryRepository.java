package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerQueueEntryRepository extends JpaRepository<CustomerQueueEntryEntity, Long> {
    List<CustomerQueueEntryEntity> findByCustomerIdOrderByJoinedAtDesc(Long customerId);
    List<CustomerQueueEntryEntity> findByUserIdOrderByJoinedAtDesc(Long userId);
}
