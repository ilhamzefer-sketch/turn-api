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
        if (user.sessionId() == null) {
            return false;
        }
        if (user.userId() != null) {
            return refreshTokenRepository.existsByIdAndUserTypeAndUserIdAndRevokedFalseAndExpiresAtAfter(
                    user.sessionId(),
                    user.userType(),
                    user.userId(),
                    LocalDateTime.now()
            );
        }
        return user.username() != null
                && refreshTokenRepository.existsByIdAndUserTypeAndUsernameAndRevokedFalseAndExpiresAtAfter(
                        user.sessionId(),
                        user.userType(),
                        user.username(),
                        LocalDateTime.now()
                );
    }
}
