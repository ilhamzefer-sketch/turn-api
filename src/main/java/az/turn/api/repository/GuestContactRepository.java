package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface GuestContactRepository extends JpaRepository<GuestContactEntity, Long> {
    Optional<GuestContactEntity> findByNormalizedPhone(String normalizedPhone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select contact from GuestContactEntity contact where contact.normalizedPhone = :normalizedPhone")
    Optional<GuestContactEntity> findByNormalizedPhoneForUpdate(String normalizedPhone);

    @Query("select contact from GuestContactEntity contact where contact.normalizedPhone is not null "
            + "and contact.createdAt < :cutoff "
            + "and not exists (select entry.id from LiveQueueEntryEntity entry "
            + "where entry.guestContact = contact "
            + "and (entry.updatedAt >= :cutoff or entry.status in :activeLiveStatuses)) "
            + "and not exists (select booking.id from PlannedBookingEntity booking "
            + "where booking.guestContact = contact and (booking.updatedAt >= :cutoff or booking.status = :activeStatus))")
    List<GuestContactEntity> findAnonymizationCandidates(
            LocalDateTime cutoff,
            List<LiveQueueEntryStatus> activeLiveStatuses,
            PlannedBookingStatus activeStatus
    );
}
