package az.turn.api;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Component
public class SessionMapper {
    private final Clock clock;

    public SessionMapper(Clock clock) {
        this.clock = clock;
    }

    public SessionInfoDto toInfo(RefreshTokenEntity session) {
        return new SessionInfoDto(
                session.getId(),
                OffsetDateTime.now(clock),
                offset(session.getLastActivityAt()),
                offset(session.getIdleExpiresAt()),
                offset(session.getAbsoluteExpiresAt())
        );
    }

    public UserSessionDto toUserSession(RefreshTokenEntity session, Long currentSessionId) {
        return new UserSessionDto(
                session.getId(),
                session.getId().equals(currentSessionId),
                session.getUserAgent(),
                session.getIpAddress(),
                offset(session.getCreatedAt()),
                offset(session.getLastUsedAt()),
                offset(session.getLastActivityAt()),
                offset(session.getIdleExpiresAt()),
                offset(session.getAbsoluteExpiresAt())
        );
    }

    private OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atZone(clock.getZone()).toOffsetDateTime();
    }
}
