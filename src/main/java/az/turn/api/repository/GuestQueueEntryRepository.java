package az.turn.api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GuestQueueEntryRepository extends JpaRepository<GuestQueueEntryEntity, Long> {
    List<GuestQueueEntryEntity> findByLinkedUserIdOrderByJoinedAtDesc(Long userId);

    @Modifying
    @Query("update GuestQueueEntryEntity entry set entry.linkedUser = :user "
            + "where entry.linkedUser is null and entry.normalizedPhone = :normalizedPhone")
    int linkUnclaimedEntries(UserEntity user, String normalizedPhone);
}
