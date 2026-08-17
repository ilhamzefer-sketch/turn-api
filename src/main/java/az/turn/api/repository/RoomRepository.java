package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {
    List<RoomEntity> findByBranchBusinessIdOrderByCreatedAtAsc(Long businessId);
    List<RoomEntity> findByBranchIdOrderByCreatedAtAsc(Long branchId);
    Optional<RoomEntity> findByIndividualWorkspaceId(Long workspaceId);
    boolean existsByBranchIdAndStatusNot(Long branchId, RoomStatus status);
}
