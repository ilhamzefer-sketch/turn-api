package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByNormalizedPhone(String normalizedPhone);
    boolean existsByNormalizedPhone(String normalizedPhone);
    long countByStatus(UserStatus status);

    @Query("select user from UserEntity user where :search is null "
            + "or lower(user.firstName) like :search "
            + "or lower(user.lastName) like :search "
            + "or lower(concat(user.firstName, ' ', user.lastName)) like :search "
            + "or user.normalizedPhone like :search "
            + "order by user.createdAt desc, user.id desc")
    Page<UserEntity> searchForAdmin(String search, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserEntity user where user.normalizedPhone = :normalizedPhone")
    Optional<UserEntity> findByNormalizedPhoneForUpdate(String normalizedPhone);
}
