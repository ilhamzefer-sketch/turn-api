package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditEventRepository extends JpaRepository<PlatformAuditEventEntity, Long> {
}
