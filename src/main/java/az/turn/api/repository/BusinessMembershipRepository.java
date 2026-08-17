package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BusinessMembershipRepository extends JpaRepository<BusinessMembershipEntity, Long> {
    Optional<BusinessMembershipEntity> findByBusinessIdAndUserId(Long businessId, Long userId);
    List<BusinessMembershipEntity> findByBusinessIdOrderByInvitedAtAsc(Long businessId);
    List<BusinessMembershipEntity> findByUserIdAndStatusOrderByInvitedAtAsc(
            Long userId,
            BusinessMembershipStatus status
    );
    long countByBusinessIdAndCreatedPendingUserTrueAndInvitedAtAfter(Long businessId, LocalDateTime after);
    long countByInvitedByUserIdAndCreatedPendingUserTrueAndInvitedAtAfter(Long userId, LocalDateTime after);
    long countByBusinessIdAndStatus(Long businessId, BusinessMembershipStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from BusinessMembershipEntity membership "
            + "where membership.business.id = :businessId and membership.user.id = :userId")
    Optional<BusinessMembershipEntity> findByBusinessIdAndUserIdForUpdate(Long businessId, Long userId);
}
