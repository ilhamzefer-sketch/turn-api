package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByNormalizedPhone(String normalizedPhone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserEntity user where user.normalizedPhone = :normalizedPhone")
    Optional<UserEntity> findByNormalizedPhoneForUpdate(String normalizedPhone);
}
