package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {
    List<RoomEntity> findByBranchBusinessIdOrderByCreatedAtAsc(Long businessId);
    List<RoomEntity> findByBranchIdOrderByCreatedAtAsc(Long branchId);
    Optional<RoomEntity> findByIndividualWorkspaceId(Long workspaceId);
    boolean existsByBranchIdAndStatusNot(Long branchId, RoomStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from RoomEntity room where room.id = :roomId")
    Optional<RoomEntity> findByIdForUpdate(Long roomId);
}
