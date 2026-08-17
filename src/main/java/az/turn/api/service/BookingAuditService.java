package az.turn.api;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BookingAuditService {
    private final BookingAuditEventRepository repository;

    public BookingAuditService(BookingAuditEventRepository repository) {
        this.repository = repository;
    }

    public void record(
            PlannedBookingEntity booking,
            BookingAuditAction action,
            UserEntity actor,
            LocalDateTime oldStartAt,
            LocalDateTime oldEndAt,
            LocalDateTime newStartAt,
            LocalDateTime newEndAt,
            String reason,
            boolean participantInformed
    ) {
        BookingAuditEventEntity event = new BookingAuditEventEntity();
        event.setBooking(booking);
        event.setAction(action);
        event.setActorUser(actor);
        event.setOldStartAt(oldStartAt);
        event.setOldEndAt(oldEndAt);
        event.setNewStartAt(newStartAt);
        event.setNewEndAt(newEndAt);
        event.setReason(reason);
        event.setParticipantInformed(participantInformed);
        repository.save(event);
    }
}
