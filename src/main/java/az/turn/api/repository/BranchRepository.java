package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchRepository extends JpaRepository<BranchEntity, Long> {
    List<BranchEntity> findByBusinessIdOrderByCreatedAtAsc(Long businessId);
}
