package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndividualWorkspaceRepository extends JpaRepository<IndividualWorkspaceEntity, Long> {
    Optional<IndividualWorkspaceEntity> findByOwnerUserId(Long ownerUserId);
    boolean existsByOwnerUserId(Long ownerUserId);
}
