package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PlannedBookingRepository extends JpaRepository<PlannedBookingEntity, Long> {
    Optional<PlannedBookingEntity> findByIdAndRoomId(Long id, Long roomId);

    @Query("select booking.room.id from PlannedBookingEntity booking where booking.id = :bookingId")
    Optional<Long> findRoomIdById(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from PlannedBookingEntity booking where booking.id = :bookingId")
    Optional<PlannedBookingEntity> findByIdForUpdate(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from PlannedBookingEntity booking "
            + "where booking.id = :bookingId and booking.room.id = :roomId")
    Optional<PlannedBookingEntity> findByIdAndRoomIdForUpdate(Long bookingId, Long roomId);

    @Query("select booking from PlannedBookingEntity booking "
            + "where booking.room.id = :roomId and booking.status = :status "
            + "and booking.startAt < :endAt and booking.blockingEndAt > :startAt "
            + "order by booking.startAt")
    List<PlannedBookingEntity> findOverlapping(
            Long roomId,
            PlannedBookingStatus status,
            LocalDateTime startAt,
            LocalDateTime endAt
    );

    List<PlannedBookingEntity> findByRoomIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            Long roomId,
            LocalDateTime startAt,
            LocalDateTime endAt
    );

    List<PlannedBookingEntity> findByRoomIdAndStatusOrderByStartAtAsc(
            Long roomId,
            PlannedBookingStatus status
    );

    @Query("select booking from PlannedBookingEntity booking "
            + "left join booking.guestContact guest left join guest.linkedUser linkedUser "
            + "where booking.user.id = :userId or linkedUser.id = :userId "
            + "order by booking.startAt desc")
    List<PlannedBookingEntity> findUserHistory(Long userId);

    boolean existsByRoomIdAndUserIdAndStatus(
            Long roomId,
            Long userId,
            PlannedBookingStatus status
    );

    @Query("select count(booking) > 0 from PlannedBookingEntity booking "
            + "left join booking.guestContact guest left join guest.linkedUser linkedUser "
            + "where booking.room.id = :roomId and booking.status = :status "
            + "and (booking.user.id = :userId or linkedUser.id = :userId)")
    boolean hasActiveBookingForUser(
            Long roomId,
            Long userId,
            PlannedBookingStatus status
    );

    boolean existsByRoomIdAndStatusAndStartAtAfter(
            Long roomId,
            PlannedBookingStatus status,
            LocalDateTime startAt
    );
}
