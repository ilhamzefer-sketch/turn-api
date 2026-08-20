package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenHistoryRepository extends JpaRepository<RefreshTokenHistoryEntity, Long> {
    Optional<RefreshTokenHistoryEntity> findByTokenHash(String tokenHash);
}
