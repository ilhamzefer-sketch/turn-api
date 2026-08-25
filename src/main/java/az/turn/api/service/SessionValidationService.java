package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SessionValidationService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionPrincipalService sessionPrincipalService;
    private final SessionPolicyService sessionPolicyService;
    private final SessionMapper sessionMapper;
    private final SessionAuditService sessionAuditService;
    private final Clock clock;

    public SessionValidationService(
            RefreshTokenRepository refreshTokenRepository,
            SessionPrincipalService sessionPrincipalService,
            SessionPolicyService sessionPolicyService,
            SessionMapper sessionMapper,
            SessionAuditService sessionAuditService,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionPrincipalService = sessionPrincipalService;
        this.sessionPolicyService = sessionPolicyService;
        this.sessionMapper = sessionMapper;
        this.sessionAuditService = sessionAuditService;
        this.clock = clock;
    }

    @Transactional
    public SessionState validateAccess(AuthenticatedUser principal) {
        if (principal.sessionId() == null) {
            return SessionState.SESSION_NOT_FOUND;
        }
        RefreshTokenEntity session = refreshTokenRepository.findById(principal.sessionId()).orElse(null);
        if (session == null || !matches(session, principal)) {
            return SessionState.SESSION_NOT_FOUND;
        }
        return evaluate(session, principal, LocalDateTime.now(clock));
    }

    public void requireRefreshActive(RefreshTokenEntity session) {
        AuthenticatedUser principal = principal(session);
        SessionState state = evaluate(session, principal, LocalDateTime.now(clock));
        if (state != SessionState.ACTIVE) {
            throw exception(state);
        }
    }

    @Transactional
    public SessionInfoDto getSessionInfo(AuthenticatedUser principal) {
        RefreshTokenEntity session = requireLockedSession(principal);
        LocalDateTime now = LocalDateTime.now(clock);
        SessionState state = evaluate(session, principal, now);
        if (state != SessionState.ACTIVE) {
            throw exception(state);
        }
        return sessionMapper.toInfo(session);
    }

    @Transactional
    public SessionInfoDto recordActivity(AuthenticatedUser principal) {
        RefreshTokenEntity session = requireLockedSession(principal);
        LocalDateTime now = LocalDateTime.now(clock);
        SessionState state = evaluate(session, principal, now);
        if (state != SessionState.ACTIVE) {
            throw exception(state);
        }
        LocalDateTime idleExpiresAt = now.plus(sessionPolicyService.idleTimeout(session.getUserType()));
        if (idleExpiresAt.isAfter(session.getAbsoluteExpiresAt())) {
            idleExpiresAt = session.getAbsoluteExpiresAt();
        }
        session.setLastActivityAt(now);
        session.setIdleExpiresAt(idleExpiresAt);
        session.setExpiresAt(idleExpiresAt);
        return sessionMapper.toInfo(session);
    }

    public void revoke(RefreshTokenEntity session, SessionRevocationReason reason) {
        if (!session.isRevoked()) {
            session.setRevoked(true);
            session.setRevokedAt(LocalDateTime.now(clock));
            session.setRevokeReason(reason);
            sessionAuditService.record(session, "SESSION_REVOKED", reason.name());
        }
    }

    private RefreshTokenEntity requireLockedSession(AuthenticatedUser principal) {
        if (principal.sessionId() == null) {
            throw exception(SessionState.SESSION_NOT_FOUND);
        }
        RefreshTokenEntity session = refreshTokenRepository.findByIdForUpdate(principal.sessionId())
                .orElseThrow(() -> exception(SessionState.SESSION_NOT_FOUND));
        if (!matches(session, principal)) {
            throw exception(SessionState.SESSION_NOT_FOUND);
        }
        return session;
    }

    private SessionState evaluate(
            RefreshTokenEntity session,
            AuthenticatedUser principal,
            LocalDateTime now
    ) {
        if (session.isRevoked()) {
            return SessionState.SESSION_REVOKED;
        }
        if (!session.getAbsoluteExpiresAt().isAfter(now)) {
            revoke(session, SessionRevocationReason.ABSOLUTE_TIMEOUT);
            return SessionState.SESSION_ABSOLUTE_TIMEOUT;
        }
        if (!session.getIdleExpiresAt().isAfter(now)) {
            revoke(session, SessionRevocationReason.IDLE_TIMEOUT);
            return SessionState.SESSION_IDLE_TIMEOUT;
        }
        PrincipalState principalState = sessionPrincipalService.resolve(principal);
        if (!principalState.active()) {
            revoke(session, SessionRevocationReason.ACCOUNT_DISABLED);
            return SessionState.ACCOUNT_DISABLED;
        }
        if (session.getPrincipalVersion() == null) {
            session.setPrincipalVersion(principalState.version());
        } else if (!secureEquals(session.getPrincipalVersion(), principalState.version())) {
            revoke(session, SessionRevocationReason.CREDENTIALS_CHANGED);
            return SessionState.CREDENTIALS_CHANGED;
        }
        return SessionState.ACTIVE;
    }

    private boolean matches(RefreshTokenEntity session, AuthenticatedUser principal) {
        return session.getUserType() == principal.userType()
                && Objects.equals(session.getUserId(), principal.userId())
                && Objects.equals(session.getUsername(), principal.username());
    }

    private AuthenticatedUser principal(RefreshTokenEntity session) {
        return new AuthenticatedUser(
                session.getUserType(),
                session.getUserId(),
                session.getUsername(),
                session.getId()
        );
    }

    private boolean secureEquals(String left, String right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private SessionAuthenticationException exception(SessionState state) {
        String message = switch (state) {
            case SESSION_IDLE_TIMEOUT -> "Fəaliyyətsizlik səbəbilə sessiyanız bitib.";
            case SESSION_ABSOLUTE_TIMEOUT -> "Sessiyanın maksimum istifadə müddəti bitib.";
            case ACCOUNT_DISABLED -> "Hesab artıq aktiv deyil.";
            case CREDENTIALS_CHANGED -> "Giriş məlumatları dəyişdiyi üçün yenidən daxil olun.";
            default -> "Sessiya etibarsızdır və ya ləğv olunub.";
        };
        return new SessionAuthenticationException(state, message);
    }
}
