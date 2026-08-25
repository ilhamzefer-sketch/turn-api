package az.turn.api;

import org.springframework.stereotype.Service;

@Service
public class SessionAuditService {
    private final PlatformAuditService platformAuditService;

    public SessionAuditService(PlatformAuditService platformAuditService) {
        this.platformAuditService = platformAuditService;
    }

    public void record(RefreshTokenEntity session, String action, String details) {
        platformAuditService.record(
                session.getUserType().name(),
                "SESSION:" + session.getId(),
                action,
                "AUTH_SESSION",
                session.getId(),
                details
        );
    }
}
