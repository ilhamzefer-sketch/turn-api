package az.turn.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final long refreshTokenDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            @Value("${app.security.refresh-token-days:14}") long refreshTokenDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshTokenDays = refreshTokenDays;
    }

    @Transactional
    public AuthTokens issueTokens(AuthenticatedUser user) {
        String accessToken = jwtService.generateAccessToken(user);

        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        String rawRefreshToken = generateOpaqueToken();
        refreshToken.setToken(hashToken(rawRefreshToken));
        refreshToken.setUserType(user.userType());
        refreshToken.setUserId(user.userId());
        refreshToken.setUsername(user.username());
        refreshToken.setExpiresAt(jwtService.calculateRefreshExpiry(refreshTokenDays));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return new AuthTokens(accessToken, rawRefreshToken);
    }

    @Transactional
    public AuthTokens refresh(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token tapılmadı.");
        }
        RefreshTokenEntity existing = refreshTokenRepository.findByTokenForUpdate(hashToken(refreshTokenValue))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token tapilmadi."));

        if (existing.isRevoked() || existing.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token vaxti bitib ve ya legv olunub.");
        }

        existing.setRevoked(true);
        AuthenticatedUser user = new AuthenticatedUser(existing.getUserType(), existing.getUserId(), existing.getUsername());
        return issueTokens(user);
    }

    @Transactional
    public void revoke(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }
        refreshTokenRepository.findByToken(hashToken(refreshTokenValue)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
