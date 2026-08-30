package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface AdminAccountRepository extends JpaRepository<AdminAccountEntity, Long> {
    Optional<AdminAccountEntity> findByUsername(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select admin from AdminAccountEntity admin where admin.username = :username")
    Optional<AdminAccountEntity> findByUsernameForUpdate(String username);
    boolean existsByUsername(String username);
    List<AdminAccountEntity> findAllByOrderByCreatedAtAsc();
}
