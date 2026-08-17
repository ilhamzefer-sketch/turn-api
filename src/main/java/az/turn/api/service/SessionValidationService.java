package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SessionValidationService {

    private final RefreshTokenRepository refreshTokenRepository;

    public SessionValidationService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public boolean isActive(AuthenticatedUser user) {
        if (!user.isUser()) {
            return true;
        }
        if (user.sessionId() == null || user.userId() == null) {
            return false;
        }
        return refreshTokenRepository.existsByIdAndUserTypeAndUserIdAndRevokedFalseAndExpiresAtAfter(
                user.sessionId(),
                AuthUserType.USER,
                user.userId(),
                LocalDateTime.now()
        );
    }
}
