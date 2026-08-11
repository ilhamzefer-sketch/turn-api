package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestQueueEntryRepository extends JpaRepository<GuestQueueEntryEntity, Long> {
}
