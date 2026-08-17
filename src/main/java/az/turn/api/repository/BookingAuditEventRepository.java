package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingAuditEventRepository extends JpaRepository<BookingAuditEventEntity, Long> {
    List<BookingAuditEventEntity> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
