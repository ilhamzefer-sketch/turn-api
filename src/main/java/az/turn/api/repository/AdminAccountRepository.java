package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminAccountRepository extends JpaRepository<AdminAccountEntity, Long> {
    Optional<AdminAccountEntity> findByUsername(String username);
    boolean existsByUsername(String username);
    List<AdminAccountEntity> findAllByOrderByCreatedAtAsc();
}
