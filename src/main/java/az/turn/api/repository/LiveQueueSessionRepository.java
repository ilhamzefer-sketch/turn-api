package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LiveQueueSessionRepository extends JpaRepository<LiveQueueSessionEntity, Long> {
    Optional<LiveQueueSessionEntity> findByRoomIdAndOpenSlot(Long roomId, Integer openSlot);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from LiveQueueSessionEntity session where session.room.id = :roomId and session.openSlot = 1")
    Optional<LiveQueueSessionEntity> findOpenByRoomIdForUpdate(Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from LiveQueueSessionEntity session where session.id = :sessionId")
    Optional<LiveQueueSessionEntity> findByIdForUpdate(Long sessionId);

    @Query("select session.id from LiveQueueSessionEntity session "
            + "where session.status = :status and session.nextResetAt <= :now order by session.nextResetAt")
    List<Long> findDueSessionIds(
            LiveQueueSessionStatus status,
            LocalDateTime now,
            Pageable pageable
    );
}
