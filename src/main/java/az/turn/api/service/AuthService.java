package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHistoryRepository refreshTokenHistoryRepository;
    private final JwtService jwtService;
    private final SessionPolicyService sessionPolicyService;
    private final SessionPrincipalService sessionPrincipalService;
    private final SessionValidationService sessionValidationService;
    private final SessionAuditService sessionAuditService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenHistoryRepository refreshTokenHistoryRepository,
            JwtService jwtService,
            SessionPolicyService sessionPolicyService,
            SessionPrincipalService sessionPrincipalService,
            SessionValidationService sessionValidationService,
            SessionAuditService sessionAuditService,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenHistoryRepository = refreshTokenHistoryRepository;
        this.jwtService = jwtService;
        this.sessionPolicyService = sessionPolicyService;
        this.sessionPrincipalService = sessionPrincipalService;
        this.sessionValidationService = sessionValidationService;
        this.sessionAuditService = sessionAuditService;
        this.clock = clock;
    }

    @Transactional
    public AuthTokens issueTokens(AuthenticatedUser user) {
        return issueTokens(user, new SessionMetadata(null, null));
    }

    @Transactional
    public AuthTokens issueTokens(AuthenticatedUser user, SessionMetadata metadata) {
        LocalDateTime now = LocalDateTime.now(clock);
        PrincipalState principalState = sessionPrincipalService.resolve(user);
        if (!principalState.active()) {
            throw new SessionAuthenticationException(SessionState.ACCOUNT_DISABLED, "Hesab artıq aktiv deyil.");
        }
        LocalDateTime idleExpiresAt = now.plus(sessionPolicyService.idleTimeout(user.userType()));
        LocalDateTime absoluteExpiresAt = now.plus(sessionPolicyService.absoluteTimeout(user.userType()));
        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        String rawRefreshToken = generateOpaqueToken();
        refreshToken.setToken(hashToken(rawRefreshToken));
        refreshToken.setUserType(user.userType());
        refreshToken.setUserId(user.userId());
        refreshToken.setUsername(user.username());
        refreshToken.setExpiresAt(idleExpiresAt);
        refreshToken.setLastActivityAt(now);
        refreshToken.setIdleExpiresAt(idleExpiresAt);
        refreshToken.setAbsoluteExpiresAt(absoluteExpiresAt);
        refreshToken.setPrincipalVersion(principalState.version());
        refreshToken.setRevoked(false);
        refreshToken.setUserAgent(metadata.userAgent());
        refreshToken.setIpAddress(metadata.ipAddress());
        RefreshTokenEntity savedToken = refreshTokenRepository.save(refreshToken);
        sessionAuditService.record(savedToken, "SESSION_CREATED", null);

        AuthenticatedUser sessionUser = new AuthenticatedUser(
                user.userType(),
                user.userId(),
                user.username(),
                savedToken.getId()
        );
        String accessToken = jwtService.generateAccessToken(sessionUser);

        return new AuthTokens(accessToken, rawRefreshToken, absoluteExpiresAt);
    }

    @Transactional(noRollbackFor = SessionAuthenticationException.class)
    public AuthTokens refresh(String refreshTokenValue, SessionMetadata metadata) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new SessionAuthenticationException(SessionState.SESSION_NOT_FOUND, "Refresh token tapılmadı.");
        }
        String currentTokenHash = hashToken(refreshTokenValue);
        RefreshTokenEntity existing = refreshTokenRepository.findByTokenForUpdate(currentTokenHash)
                .orElseGet(() -> rejectReusedToken(currentTokenHash));

        sessionValidationService.requireRefreshActive(existing);

        RefreshTokenHistoryEntity history = new RefreshTokenHistoryEntity();
        history.setRefreshTokenId(existing.getId());
        history.setTokenHash(currentTokenHash);
        refreshTokenHistoryRepository.save(history);

        String rawRefreshToken = generateOpaqueToken();
        existing.setToken(hashToken(rawRefreshToken));
        existing.setLastUsedAt(LocalDateTime.now(clock));
        if (metadata.userAgent() != null) {
            existing.setUserAgent(metadata.userAgent());
        }
        if (metadata.ipAddress() != null) {
            existing.setIpAddress(metadata.ipAddress());
        }
        sessionAuditService.record(existing, "SESSION_REFRESHED", null);

        AuthenticatedUser user = new AuthenticatedUser(
                existing.getUserType(),
                existing.getUserId(),
                existing.getUsername(),
                existing.getId()
        );
        return new AuthTokens(jwtService.generateAccessToken(user), rawRefreshToken, existing.getAbsoluteExpiresAt());
    }

    @Transactional
    public void revoke(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        String tokenHash = hashToken(refreshTokenValue);
        RefreshTokenEntity session = refreshTokenRepository.findByTokenForUpdate(tokenHash)
                .orElseGet(() -> refreshTokenHistoryRepository.findByTokenHash(tokenHash)
                        .flatMap(history -> refreshTokenRepository.findByIdForUpdate(history.getRefreshTokenId()))
                        .orElse(null));
        if (session != null) {
            sessionValidationService.revoke(session, SessionRevocationReason.LOGOUT);
        }
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private RefreshTokenEntity rejectReusedToken(String tokenHash) {
        RefreshTokenEntity reusedSession = refreshTokenHistoryRepository.findByTokenHash(tokenHash)
                .flatMap(history -> refreshTokenRepository.findByIdForUpdate(history.getRefreshTokenId()))
                .orElse(null);
        if (reusedSession != null) {
            sessionValidationService.revoke(reusedSession, SessionRevocationReason.TOKEN_REUSE);
            throw new SessionAuthenticationException(
                    SessionState.REFRESH_TOKEN_REUSE,
                    "Təhlükəsizlik səbəbilə sessiya dayandırıldı. Yenidən daxil olun."
            );
        }
        throw new SessionAuthenticationException(SessionState.SESSION_NOT_FOUND, "Refresh token etibarsızdır.");
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 mövcud deyil.", exception);
        }
    }
}
