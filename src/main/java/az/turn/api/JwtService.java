package az.turn.api;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenMinutes;

    public JwtService(
            @Value("${app.security.jwt-secret:local-only-default-secret-change-before-production-1234567890}") String jwtSecret,
            @Value("${app.security.access-token-minutes:15}") long accessTokenMinutes
    ) {
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
    }

    public String generateAccessToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenMinutes * 60);

        return Jwts.builder()
                .subject(user.username())
                .claim("userType", user.userType().name())
                .claim("userId", user.userId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    public AuthenticatedUser parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        AuthUserType userType = AuthUserType.valueOf(claims.get("userType", String.class));
        Number userId = claims.get("userId", Number.class);
        return new AuthenticatedUser(
                userType,
                userId == null ? null : userId.longValue(),
                claims.getSubject()
        );
    }

    public LocalDateTime calculateRefreshExpiry(long refreshTokenDays) {
        return LocalDateTime.ofInstant(Instant.now().plusSeconds(refreshTokenDays * 24 * 60 * 60), ZoneId.systemDefault());
    }

    private byte[] padKey(byte[] bytes) {
        byte[] padded = new byte[32];
        for (int i = 0; i < padded.length; i++) {
            padded[i] = i < bytes.length ? bytes[i] : (byte) 'x';
        }
        return padded;
    }
}
