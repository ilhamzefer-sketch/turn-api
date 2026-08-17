package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserSessionService {

    private final RefreshTokenRepository refreshTokenRepository;

    public UserSessionService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public List<UserSessionDto> getActiveSessions(long userId, Long currentSessionId) {
        return refreshTokenRepository
                .findByUserTypeAndUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        AuthUserType.USER,
                        userId,
                        LocalDateTime.now()
                )
                .stream()
                .map(session -> toDto(session, currentSessionId))
                .toList();
    }

    @Transactional
    public void revokeSession(long userId, long sessionId) {
        RefreshTokenEntity session = refreshTokenRepository
                .findByIdAndUserTypeAndUserId(sessionId, AuthUserType.USER, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sessiya tapılmadı."));
        session.setRevoked(true);
    }

    @Transactional
    public void revokeOtherSessions(long userId, Long currentSessionId) {
        if (currentSessionId == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cari sessiya müəyyən edilə bilmədi. Yenidən daxil olun.");
        }
        refreshTokenRepository.revokeOtherSessions(AuthUserType.USER, userId, currentSessionId);
    }

    @Transactional
    public void revokeAllSessions(long userId) {
        refreshTokenRepository.revokeAllForUser(AuthUserType.USER, userId);
    }

    private UserSessionDto toDto(RefreshTokenEntity session, Long currentSessionId) {
        return new UserSessionDto(
                session.getId(),
                session.getId().equals(currentSessionId),
                session.getUserAgent(),
                session.getIpAddress(),
                session.getCreatedAt(),
                session.getLastUsedAt(),
                session.getExpiresAt()
        );
    }
}
