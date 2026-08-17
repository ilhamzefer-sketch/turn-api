package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QrCredentialRepository extends JpaRepository<QrCredentialEntity, Long> {
    List<QrCredentialEntity> findByRoomIdOrderByCreatedAtDesc(Long roomId);
    Optional<QrCredentialEntity> findByIdAndRoomId(Long id, Long roomId);
    Optional<QrCredentialEntity> findByTokenHashAndActiveTrue(String tokenHash);
}
