package az.turn.api;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class GuestDataRetentionJob {
    private final GuestContactRepository guestContactRepository;
    private final Clock clock;

    public GuestDataRetentionJob(GuestContactRepository guestContactRepository, Clock clock) {
        this.guestContactRepository = guestContactRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.privacy.guest-anonymization-cron:0 30 3 * * *}")
    @Transactional
    public void anonymizeExpiredGuestContacts() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusMonths(24);
        List<GuestContactEntity> contacts = guestContactRepository.findAnonymizationCandidates(
                cutoff,
                List.of(
                        LiveQueueEntryStatus.WAITING,
                        LiveQueueEntryStatus.CURRENT,
                        LiveQueueEntryStatus.SKIPPED
                ),
                PlannedBookingStatus.ACTIVE
        );
        contacts.forEach(contact -> {
            contact.setDisplayName("Anonim");
            contact.setNormalizedPhone(null);
        });
        guestContactRepository.saveAll(contacts);
    }
}
