package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

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
    Page<UserEntity> searchForAdmin(@Param("search") String search, Pageable pageable);

    @Query("select user from UserEntity user where (:name is null "
            + "or lower(user.firstName) like :name "
            + "or lower(user.lastName) like :name "
            + "or lower(concat(user.firstName, ' ', user.lastName)) like :name) "
            + "and (:phone is null or user.normalizedPhone like :phone) "
            + "order by user.createdAt desc, user.id desc")
    Page<UserEntity> searchForAdminByNameAndPhone(@Param("name") String name, @Param("phone") String phone, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserEntity user where user.normalizedPhone = :normalizedPhone")
    Optional<UserEntity> findByNormalizedPhoneForUpdate(String normalizedPhone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserEntity user where user.id = :userId")
    Optional<UserEntity> findByIdForUpdate(long userId);
}
