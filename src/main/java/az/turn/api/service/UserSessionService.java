package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;

@Service
public class UserSessionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionMapper sessionMapper;
    private final Clock clock;

    public UserSessionService(
            RefreshTokenRepository refreshTokenRepository,
            SessionMapper sessionMapper,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionMapper = sessionMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserSessionDto> getActiveSessions(long userId, Long currentSessionId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return refreshTokenRepository
                .findByUserTypeAndUserIdAndRevokedFalseAndIdleExpiresAtAfterAndAbsoluteExpiresAtAfterOrderByCreatedAtDesc(
                        AuthUserType.USER,
                        userId,
                        now,
                        now
                )
                .stream()
                .map(session -> sessionMapper.toUserSession(session, currentSessionId))
                .toList();
    }

    @Transactional
    public void revokeSession(long userId, long sessionId) {
        RefreshTokenEntity session = refreshTokenRepository
                .findByIdAndUserTypeAndUserId(sessionId, AuthUserType.USER, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sessiya tapılmadı."));
        session.setRevoked(true);
        session.setRevokedAt(LocalDateTime.now(clock));
        session.setRevokeReason(SessionRevocationReason.MANUAL_REVOCATION);
    }

    @Transactional
    public void revokeOtherSessions(long userId, Long currentSessionId) {
        if (currentSessionId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cari sessiya müəyyən edilə bilmədi. Yenidən daxil olun.");
        }
        refreshTokenRepository.revokeOtherSessions(
                AuthUserType.USER,
                userId,
                currentSessionId,
                LocalDateTime.now(clock),
                SessionRevocationReason.MANUAL_REVOCATION
        );
    }

    @Transactional
    public void revokeAllSessions(long userId) {
        refreshTokenRepository.revokeAllForUser(
                AuthUserType.USER,
                userId,
                LocalDateTime.now(clock),
                SessionRevocationReason.MANUAL_REVOCATION
        );
    }
}
