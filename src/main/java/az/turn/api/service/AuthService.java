package az.turn.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHistoryRepository refreshTokenHistoryRepository;
    private final JwtService jwtService;
    private final long refreshTokenDays;
    private final long refreshReuseGraceSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenHistoryRepository refreshTokenHistoryRepository,
            JwtService jwtService,
            @Value("${app.security.refresh-token-days:14}") long refreshTokenDays,
            @Value("${app.security.refresh-reuse-grace-seconds:10}") long refreshReuseGraceSeconds
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenHistoryRepository = refreshTokenHistoryRepository;
        this.jwtService = jwtService;
        this.refreshTokenDays = refreshTokenDays;
        this.refreshReuseGraceSeconds = refreshReuseGraceSeconds;
    }

    @Transactional
    public AuthTokens issueTokens(AuthenticatedUser user) {
        return issueTokens(user, new SessionMetadata(null, null));
    }

    @Transactional
    public AuthTokens issueTokens(AuthenticatedUser user, SessionMetadata metadata) {

        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        String rawRefreshToken = generateOpaqueToken();
        refreshToken.setToken(hashToken(rawRefreshToken));
        refreshToken.setUserType(user.userType());
        refreshToken.setUserId(user.userId());
        refreshToken.setUsername(user.username());
        refreshToken.setExpiresAt(jwtService.calculateRefreshExpiry(refreshTokenDays));
        refreshToken.setRevoked(false);
        refreshToken.setUserAgent(metadata.userAgent());
        refreshToken.setIpAddress(metadata.ipAddress());
        RefreshTokenEntity savedToken = refreshTokenRepository.save(refreshToken);

        AuthenticatedUser sessionUser = new AuthenticatedUser(
                user.userType(),
                user.userId(),
                user.username(),
                savedToken.getId()
        );
        String accessToken = jwtService.generateAccessToken(sessionUser);

        return new AuthTokens(accessToken, rawRefreshToken);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthTokens refresh(String refreshTokenValue, SessionMetadata metadata) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token tapılmadı.");
        }
        String currentTokenHash = hashToken(refreshTokenValue);
        RefreshTokenEntity existing = refreshTokenRepository.findByTokenForUpdate(currentTokenHash)
                .orElseGet(() -> rejectReusedToken(currentTokenHash));

        if (existing.isRevoked() || existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            existing.setRevoked(true);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessiyanın vaxtı bitib və ya sessiya ləğv olunub.");
        }

        RefreshTokenHistoryEntity history = new RefreshTokenHistoryEntity();
        history.setRefreshTokenId(existing.getId());
        history.setTokenHash(currentTokenHash);
        refreshTokenHistoryRepository.save(history);

        String rawRefreshToken = generateOpaqueToken();
        existing.setToken(hashToken(rawRefreshToken));
        existing.setExpiresAt(jwtService.calculateRefreshExpiry(refreshTokenDays));
        existing.setLastUsedAt(LocalDateTime.now());
        if (metadata.userAgent() != null) {
            existing.setUserAgent(metadata.userAgent());
        }
        if (metadata.ipAddress() != null) {
            existing.setIpAddress(metadata.ipAddress());
        }

        AuthenticatedUser user = new AuthenticatedUser(
                existing.getUserType(),
                existing.getUserId(),
                existing.getUsername(),
                existing.getId()
        );
        return new AuthTokens(jwtService.generateAccessToken(user), rawRefreshToken);
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
            session.setRevoked(true);
        }
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private RefreshTokenEntity rejectReusedToken(String tokenHash) {
        refreshTokenHistoryRepository.findByTokenHash(tokenHash).ifPresent(history -> {
            RefreshTokenEntity session = refreshTokenRepository.findByIdForUpdate(history.getRefreshTokenId())
                    .orElse(null);
            if (session != null
                    && !session.isRevoked()
                    && history.getRotatedAt().isBefore(LocalDateTime.now().minusSeconds(refreshReuseGraceSeconds))) {
                session.setRevoked(true);
            }
        });
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token etibarsızdır.");
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
