package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveQueueEntryRepository extends JpaRepository<LiveQueueEntryEntity, Long> {
    List<LiveQueueEntryEntity> findBySessionIdOrderByQueuePositionAsc(Long sessionId);
    List<LiveQueueEntryEntity> findBySessionIdAndStatusInOrderByQueuePositionAsc(
            Long sessionId,
            List<LiveQueueEntryStatus> statuses
    );
    Optional<LiveQueueEntryEntity> findBySessionIdAndStatus(Long sessionId, LiveQueueEntryStatus status);
    Optional<LiveQueueEntryEntity> findFirstBySessionIdAndStatusOrderByQueuePositionAsc(
            Long sessionId,
            LiveQueueEntryStatus status
    );
    Optional<LiveQueueEntryEntity> findBySessionIdAndActiveIdentityKey(Long sessionId, String activeIdentityKey);
    Optional<LiveQueueEntryEntity> findByIdAndSessionRoomId(Long entryId, Long roomId);
    Optional<LiveQueueEntryEntity> findByPublicReference(String publicReference);
    long countBySessionIdAndStatusIn(Long sessionId, List<LiveQueueEntryStatus> statuses);
    List<LiveQueueEntryEntity> findByUserIdOrGuestContactLinkedUserIdOrderByCreatedAtDesc(
            Long userId,
            Long linkedUserId
    );
}
