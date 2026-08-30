package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenEntity token where token.token = :token")
    Optional<RefreshTokenEntity> findByTokenForUpdate(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenEntity token where token.id = :id")
    Optional<RefreshTokenEntity> findByIdForUpdate(Long id);

    List<RefreshTokenEntity> findByUserTypeAndUserIdAndRevokedFalseAndIdleExpiresAtAfterAndAbsoluteExpiresAtAfterOrderByCreatedAtDesc(
            AuthUserType userType,
            Long userId,
            LocalDateTime idleNow,
            LocalDateTime now
    );

    List<RefreshTokenEntity> findByUserTypeAndUserIdAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            AuthUserType userType,
            Long userId,
            LocalDateTime now
    );

    Optional<RefreshTokenEntity> findByIdAndUserTypeAndUserId(Long id, AuthUserType userType, Long userId);

    boolean existsByIdAndUserTypeAndUserIdAndRevokedFalseAndExpiresAtAfter(
            Long id,
            AuthUserType userType,
            Long userId,
            LocalDateTime now
    );

    boolean existsByIdAndUserTypeAndUsernameAndRevokedFalseAndExpiresAtAfter(
            Long id,
            AuthUserType userType,
            String username,
            LocalDateTime now
    );

    @Modifying
    @Query("update RefreshTokenEntity token set token.revoked = true, token.revokedAt = :revokedAt, token.revokeReason = :reason "
            + "where token.userType = :userType and token.userId = :userId and token.revoked = false")
    int revokeAllForUser(
            AuthUserType userType,
            Long userId,
            LocalDateTime revokedAt,
            SessionRevocationReason reason
    );

    @Modifying
    @Query("update RefreshTokenEntity token set token.revoked = true, token.revokedAt = :revokedAt, token.revokeReason = :reason "
            + "where token.userType = :userType and token.username = :username and token.revoked = false")
    int revokeAllForUsername(
            AuthUserType userType,
            String username,
            LocalDateTime revokedAt,
            SessionRevocationReason reason
    );

    @Modifying
    @Query("update RefreshTokenEntity token set token.revoked = true, token.revokedAt = :revokedAt, token.revokeReason = :reason "
            + "where token.userType = :userType and token.userId = :userId "
            + "and token.id <> :currentSessionId and token.revoked = false")
    int revokeOtherSessions(
            AuthUserType userType,
            Long userId,
            Long currentSessionId,
            LocalDateTime revokedAt,
            SessionRevocationReason reason
    );

    long deleteByRevokedTrueAndRevokedAtBefore(LocalDateTime cutoff);

    @Modifying
    @Query("update RefreshTokenEntity token set token.revoked = true, token.revokedAt = :now, token.revokeReason = :reason "
            + "where token.revoked = false and (token.idleExpiresAt <= :now or token.absoluteExpiresAt <= :now)")
    int revokeExpired(LocalDateTime now, SessionRevocationReason reason);
}
