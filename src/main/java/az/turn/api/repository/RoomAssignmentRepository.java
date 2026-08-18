package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomAssignmentRepository extends JpaRepository<RoomAssignmentEntity, Long> {
    Optional<RoomAssignmentEntity> findByRoomIdAndUserId(Long roomId, Long userId);
    List<RoomAssignmentEntity> findByRoomIdOrderByCreatedAtAsc(Long roomId);
    List<RoomAssignmentEntity> findByUserIdAndStatusOrderByCreatedAtAsc(
            Long userId,
            RoomAssignmentStatus status
    );
    List<RoomAssignmentEntity> findByUserIdAndStatus(Long userId, RoomAssignmentStatus status);
    List<RoomAssignmentEntity> findByUserIdAndRoomBranchBusinessIdAndStatus(
            Long userId,
            Long businessId,
            RoomAssignmentStatus status
    );
    List<RoomAssignmentEntity> findByUserIdAndRoomBranchBusinessIdAndStatusIn(
            Long userId,
            Long businessId,
            List<RoomAssignmentStatus> statuses
    );
    long countByRoomIdAndStatus(Long roomId, RoomAssignmentStatus status);
    List<RoomAssignmentEntity> findByRoomIdInAndStatusOrderByCreatedAtAsc(
            List<Long> roomIds,
            RoomAssignmentStatus status
    );
}
