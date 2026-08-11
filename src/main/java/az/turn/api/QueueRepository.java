package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QueueRepository extends JpaRepository<QueueEntity, Long> {
    List<QueueEntity> findByRegistrationIdOrderByIdAsc(Long registrationId);
    long countByRegistrationId(Long registrationId);
    Optional<QueueEntity> findByQrToken(String qrToken);
}
