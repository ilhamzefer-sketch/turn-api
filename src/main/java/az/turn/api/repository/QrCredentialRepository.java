package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QrCredentialRepository extends JpaRepository<QrCredentialEntity, Long> {
    List<QrCredentialEntity> findByRoomIdOrderByCreatedAtDesc(Long roomId);
    Optional<QrCredentialEntity> findByIdAndRoomId(Long id, Long roomId);
    @Query("select credential from QrCredentialEntity credential "
            + "where credential.active = true and "
            + "(credential.tokenHash = :tokenHash or credential.legacyTokenHash = :tokenHash)")
    Optional<QrCredentialEntity> findActiveByCurrentOrLegacyTokenHash(@Param("tokenHash") String tokenHash);
}
