package az.turn.api;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenMinutes;
    private final long privilegedAccessTokenMinutes;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public JwtService(
            @Value("${app.security.jwt-secret:local-only-default-secret-change-before-production-1234567890}") String jwtSecret,
            @Value("${app.security.access-token-minutes:10}") long accessTokenMinutes,
            @Value("${app.security.privileged-access-token-minutes:5}") long privilegedAccessTokenMinutes,
            @Value("${app.security.jwt-issuer:novbetime-api}") String issuer,
            @Value("${app.security.jwt-audience:novbetime-web}") String audience,
            Clock clock
    ) {
        if (accessTokenMinutes <= 0 || privilegedAccessTokenMinutes <= 0) {
            throw new IllegalStateException("Access token lifetimes must be positive");
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalStateException("JWT issuer and audience must be configured");
        }
        byte[] keyBytes;
        if (jwtSecret.matches("^[A-Za-z0-9+/=]+$") && jwtSecret.length() >= 44) {
            try {
                keyBytes = Decoders.BASE64.decode(jwtSecret);
            } catch (Exception exception) {
                keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            }
        } else {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes.length >= 32 ? keyBytes : padKey(keyBytes));
        this.accessTokenMinutes = accessTokenMinutes;
        this.privilegedAccessTokenMinutes = privilegedAccessTokenMinutes;
        this.issuer = issuer;
        this.audience = audience;
        this.clock = clock;
    }

    public String generateAccessToken(AuthenticatedUser user) {
        Instant now = clock.instant();
        long tokenMinutes = user.isAdmin() || user.isQueueManager()
                ? privilegedAccessTokenMinutes
                : accessTokenMinutes;
        Instant expiresAt = now.plusSeconds(tokenMinutes * 60);

        return Jwts.builder()
                .subject(user.username())
                .issuer(issuer)
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .claim("userType", user.userType().name())
                .claim("userId", user.userId())
                .claim("sessionId", user.sessionId())
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        AuthUserType userType = AuthUserType.valueOf(claims.get("userType", String.class));
        Number userId = claims.get("userId", Number.class);
        Number sessionId = claims.get("sessionId", Number.class);
        return new AuthenticatedUser(
                userType,
                userId == null ? null : userId.longValue(),
                claims.getSubject(),
                sessionId == null ? null : sessionId.longValue()
        );
    }

    private byte[] padKey(byte[] bytes) {
        byte[] padded = new byte[32];
        for (int i = 0; i < padded.length; i++) {
            padded[i] = i < bytes.length ? bytes[i] : (byte) 'x';
        }
        return padded;
    }
}
