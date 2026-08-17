package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ServiceRatingRepository extends JpaRepository<ServiceRatingEntity, Long> {
    Optional<ServiceRatingEntity> findByLiveQueueEntryId(Long entryId);
    Optional<ServiceRatingEntity> findByPlannedBookingId(Long bookingId);
    long countByRoomId(Long roomId);

    @Query("select avg(rating.score) from ServiceRatingEntity rating where rating.room.id = :roomId")
    Double averageScoreByRoomId(Long roomId);

    List<ServiceRatingEntity> findByRoomIdOrderByCreatedAtDesc(Long roomId);
}
