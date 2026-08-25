package az.turn.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class SessionPolicyService {
    private final Duration userIdleTimeout;
    private final Duration privilegedIdleTimeout;
    private final Duration adminIdleTimeout;
    private final Duration userAbsoluteTimeout;
    private final Duration privilegedAbsoluteTimeout;
    private final Duration adminAbsoluteTimeout;

    public SessionPolicyService(
            @Value("${app.security.session.user-idle-minutes:30}") long userIdleMinutes,
            @Value("${app.security.session.privileged-idle-minutes:15}") long privilegedIdleMinutes,
            @Value("${app.security.session.admin-idle-minutes:10}") long adminIdleMinutes,
            @Value("${app.security.session.user-absolute-hours:12}") long userAbsoluteHours,
            @Value("${app.security.session.privileged-absolute-hours:8}") long privilegedAbsoluteHours,
            @Value("${app.security.session.admin-absolute-hours:4}") long adminAbsoluteHours
    ) {
        this.userIdleTimeout = positiveMinutes(userIdleMinutes, "user idle timeout");
        this.privilegedIdleTimeout = positiveMinutes(privilegedIdleMinutes, "privileged idle timeout");
        this.adminIdleTimeout = positiveMinutes(adminIdleMinutes, "admin idle timeout");
        this.userAbsoluteTimeout = positiveHours(userAbsoluteHours, "user absolute timeout");
        this.privilegedAbsoluteTimeout = positiveHours(privilegedAbsoluteHours, "privileged absolute timeout");
        this.adminAbsoluteTimeout = positiveHours(adminAbsoluteHours, "admin absolute timeout");
        requireLonger(this.userAbsoluteTimeout, this.userIdleTimeout, "user session");
        requireLonger(this.privilegedAbsoluteTimeout, this.privilegedIdleTimeout, "privileged session");
        requireLonger(this.adminAbsoluteTimeout, this.adminIdleTimeout, "admin session");
    }

    public Duration idleTimeout(AuthUserType userType) {
        return switch (userType) {
            case ADMIN -> adminIdleTimeout;
            case QUEUE_MANAGER, REGISTRATION -> privilegedIdleTimeout;
            case USER, CUSTOMER -> userIdleTimeout;
        };
    }

    public Duration absoluteTimeout(AuthUserType userType) {
        return switch (userType) {
            case ADMIN -> adminAbsoluteTimeout;
            case QUEUE_MANAGER, REGISTRATION -> privilegedAbsoluteTimeout;
            case USER, CUSTOMER -> userAbsoluteTimeout;
        };
    }

    private Duration positiveMinutes(long value, String name) {
        if (value <= 0) {
            throw new IllegalStateException(name + " must be positive");
        }
        return Duration.ofMinutes(value);
    }

    private Duration positiveHours(long value, String name) {
        if (value <= 0) {
            throw new IllegalStateException(name + " must be positive");
        }
        return Duration.ofHours(value);
    }

    private void requireLonger(Duration absolute, Duration idle, String name) {
        if (absolute.compareTo(idle) <= 0) {
            throw new IllegalStateException(name + " absolute timeout must exceed idle timeout");
        }
    }
}
