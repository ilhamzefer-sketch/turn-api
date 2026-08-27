package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {
    List<RoomEntity> findByBranchBusinessIdOrderByCreatedAtAsc(Long businessId);
    List<RoomEntity> findByBranchIdOrderByCreatedAtAsc(Long branchId);
    List<RoomEntity> findByIndividualWorkspaceIdAndStatusNotOrderByCreatedAtDesc(
            Long workspaceId,
            RoomStatus status
    );
    boolean existsByIndividualWorkspaceIdAndStatusNot(Long workspaceId, RoomStatus status);
    boolean existsByBranchIdAndStatusNot(Long branchId, RoomStatus status);
    long countByBranchBusinessIdAndStatusNot(Long businessId, RoomStatus status);
    long countByIndividualWorkspaceIdAndStatusNot(Long workspaceId, RoomStatus status);
    long countByStatusNot(RoomStatus status);

    @Query(value = """
            select distinct room from RoomEntity room
            left join fetch room.branch branch
            left join fetch branch.business business
            left join fetch business.category category
            left join fetch room.individualWorkspace workspace
            where room.status = :publishedStatus
              and room.visibility = :publicVisibility
              and (:mode is null or room.reservationMode = :mode)
              and (:categoryId is null or category.id = :categoryId)
              and (:city is null or lower(branch.city) = :city)
              and (:district is null or lower(branch.district) = :district)
              and (:search is null or lower(room.name) like :search
                  or lower(coalesce(room.description, '')) like :search
                  or lower(coalesce(business.name, '')) like :search
                  or lower(coalesce(business.customSubcategory, '')) like :search
                  or lower(coalesce(category.nameAz, '')) like :search
                  or lower(coalesce(branch.name, '')) like :search
                  or lower(coalesce(branch.address, '')) like :search
                  or lower(coalesce(branch.city, '')) like :search
                  or lower(coalesce(branch.district, '')) like :search
                  or lower(coalesce(workspace.name, '')) like :search
                  or lower(coalesce(room.personalPublicAddress, '')) like :search)
            """,
            countQuery = """
            select count(distinct room.id) from RoomEntity room
            left join room.branch branch
            left join branch.business business
            left join business.category category
            left join room.individualWorkspace workspace
            where room.status = :publishedStatus
              and room.visibility = :publicVisibility
              and (:mode is null or room.reservationMode = :mode)
              and (:categoryId is null or category.id = :categoryId)
              and (:city is null or lower(branch.city) = :city)
              and (:district is null or lower(branch.district) = :district)
              and (:search is null or lower(room.name) like :search
                  or lower(coalesce(room.description, '')) like :search
                  or lower(coalesce(business.name, '')) like :search
                  or lower(coalesce(business.customSubcategory, '')) like :search
                  or lower(coalesce(category.nameAz, '')) like :search
                  or lower(coalesce(branch.name, '')) like :search
                  or lower(coalesce(branch.address, '')) like :search
                  or lower(coalesce(branch.city, '')) like :search
                  or lower(coalesce(branch.district, '')) like :search
                  or lower(coalesce(workspace.name, '')) like :search
                  or lower(coalesce(room.personalPublicAddress, '')) like :search)
            """)
    Page<RoomEntity> searchPublic(
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            @Param("city") String city,
            @Param("district") String district,
            @Param("mode") ReservationMode mode,
            @Param("publishedStatus") RoomStatus publishedStatus,
            @Param("publicVisibility") RoomVisibility publicVisibility,
            Pageable pageable
    );

    @Query("""
            select room from RoomEntity room
            left join fetch room.branch branch
            left join fetch branch.business business
            left join fetch business.category
            left join fetch room.individualWorkspace
            where room.id = :roomId
              and room.status = :publishedStatus
              and room.visibility <> :privateVisibility
            """)
    Optional<RoomEntity> findPubliclyAccessibleById(
            @Param("roomId") Long roomId,
            @Param("publishedStatus") RoomStatus publishedStatus,
            @Param("privateVisibility") RoomVisibility privateVisibility
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from RoomEntity room where room.id = :roomId")
    Optional<RoomEntity> findByIdForUpdate(Long roomId);

    @Query("select room.id from RoomEntity room "
            + "where room.status = :status and room.reservationMode = :mode "
            + "and not exists (select session.id from LiveQueueSessionEntity session "
            + "where session.room = room and session.openSlot = 1) order by room.id")
    List<Long> findIdsRequiringLiveQueueSession(
            RoomStatus status,
            ReservationMode mode,
            Pageable pageable
    );
}
