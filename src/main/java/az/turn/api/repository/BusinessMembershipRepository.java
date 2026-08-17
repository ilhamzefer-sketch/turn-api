package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
