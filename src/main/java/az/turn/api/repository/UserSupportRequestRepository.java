package az.turn.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UserSupportRequestRepository extends JpaRepository<UserSupportRequestEntity, Long> {
    @Query("select request from UserSupportRequestEntity request where request.user.id = :userId "
            + "order by request.createdAt desc, request.id desc")
    Page<UserSupportRequestEntity> findMine(@Param("userId") long userId, Pageable pageable);

    Optional<UserSupportRequestEntity> findByIdAndUserId(long id, long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from UserSupportRequestEntity request where request.id = :id")
    Optional<UserSupportRequestEntity> findByIdForUpdate(@Param("id") long id);

    @Query("select request from UserSupportRequestEntity request "
            + "where (:requestType is null or request.requestType = :requestType) "
            + "and (:status is null or request.status = :status) "
            + "order by request.createdAt desc, request.id desc")
    Page<UserSupportRequestEntity> searchForAdmin(
            @Param("requestType") UserSupportRequestType requestType,
            @Param("status") SupportRequestStatus status,
            Pageable pageable
    );
}
