package az.turn.api;

import org.springframework.stereotype.Service;

@Service
public class PlatformAuditService {
    private final PlatformAuditEventRepository repository;

    public PlatformAuditService(PlatformAuditEventRepository repository) {
        this.repository = repository;
    }

    public void record(
            String actorType,
            String actorReference,
            String action,
            String targetType,
            Long targetId,
            String details
    ) {
        PlatformAuditEventEntity event = new PlatformAuditEventEntity();
        event.setActorType(actorType);
        event.setActorReference(actorReference);
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setDetails(details);
        repository.save(event);
    }
}
