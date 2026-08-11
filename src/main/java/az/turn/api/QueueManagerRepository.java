package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QueueManagerRepository extends JpaRepository<QueueManagerEntity, Long> {
    Optional<QueueManagerEntity> findByUsername(String username);
    Optional<QueueManagerEntity> findByQueueId(Long queueId);
    boolean existsByUsername(String username);
}
