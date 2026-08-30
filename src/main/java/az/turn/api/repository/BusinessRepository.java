package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface BusinessRepository extends JpaRepository<BusinessEntity, Long> {
    boolean existsByPrimaryOwnerUserIdAndStatus(Long userId, ProviderStatus status);
    long countByStatusNot(ProviderStatus status);

    @EntityGraph(attributePaths = "primaryOwnerUser")
    @Query("select business from BusinessEntity business where :search is null "
            + "or lower(business.name) like :search "
            + "or lower(business.primaryOwnerUser.firstName) like :search "
            + "or lower(business.primaryOwnerUser.lastName) like :search "
            + "or business.normalizedPhone like :search "
            + "order by business.createdAt desc, business.id desc")
    Page<BusinessEntity> searchForAdmin(String search, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select business from BusinessEntity business where business.id = :id")
    Optional<BusinessEntity> findByIdForUpdate(Long id);
}
