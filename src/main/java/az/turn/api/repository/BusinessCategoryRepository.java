package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessCategoryRepository extends JpaRepository<BusinessCategoryEntity, Long> {
    List<BusinessCategoryEntity> findByActiveTrueOrderByDisplayOrderAscNameAzAsc();
    Optional<BusinessCategoryEntity> findByCodeAndActiveTrue(String code);
}
