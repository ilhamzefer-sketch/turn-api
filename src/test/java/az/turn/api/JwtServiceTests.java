package az.turn.api;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTests {
    private static final String SECRET = "jwt-test-secret-jwt-test-secret-jwt-test-secret-2026";

    @Test
    void validatesIssuerAudienceSignatureAndSessionIdentity() {
        JwtService issuer = service("novbetime-web");
        AuthenticatedUser principal = new AuthenticatedUser(AuthUserType.USER, 42L, "+994501112233", 77L);

        String token = issuer.generateAccessToken(principal);

        assertThat(issuer.parseAccessToken(token)).isEqualTo(principal);
        assertThatThrownBy(() -> service("another-client").parseAccessToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsInvalidLifetimeConfiguration() {
        assertThatThrownBy(() -> new JwtService(SECRET, 0, 5, "issuer", "audience", Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    private JwtService service(String audience) {
        return new JwtService(SECRET, 10, 5, "novbetime-api", audience, Clock.systemUTC());
    }
}
