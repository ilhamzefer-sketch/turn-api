package az.turn.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionPolicyServiceTests {
    @Test
    void appliesDifferentPoliciesToUserPrivilegedAndAdminSessions() {
        SessionPolicyService policy = new SessionPolicyService(30, 15, 10, 12, 8, 4);

        assertThat(policy.idleTimeout(AuthUserType.USER)).isEqualTo(Duration.ofMinutes(30));
        assertThat(policy.idleTimeout(AuthUserType.QUEUE_MANAGER)).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.idleTimeout(AuthUserType.ADMIN)).isEqualTo(Duration.ofMinutes(10));
        assertThat(policy.absoluteTimeout(AuthUserType.USER)).isEqualTo(Duration.ofHours(12));
        assertThat(policy.absoluteTimeout(AuthUserType.ADMIN)).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void rejectsNonPositiveTimeouts() {
        assertThatThrownBy(() -> new SessionPolicyService(0, 15, 10, 12, 8, 4))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsAbsoluteTimeoutThatDoesNotExceedIdleTimeout() {
        assertThatThrownBy(() -> new SessionPolicyService(60, 15, 10, 1, 8, 4))
                .isInstanceOf(IllegalStateException.class);
    }
}
