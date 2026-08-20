package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IndividualWorkspaceRepository extends JpaRepository<IndividualWorkspaceEntity, Long> {
    Optional<IndividualWorkspaceEntity> findByOwnerUserId(Long ownerUserId);
    boolean existsByOwnerUserId(Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select workspace from IndividualWorkspaceEntity workspace where workspace.id = :workspaceId")
    Optional<IndividualWorkspaceEntity> findByIdForUpdate(@Param("workspaceId") Long workspaceId);
}
