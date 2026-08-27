package az.turn.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.live-queue.provisioning-enabled", havingValue = "true", matchIfMissing = true)
public class LiveQueueSessionProvisioningJob {
    private static final Logger logger = LoggerFactory.getLogger(LiveQueueSessionProvisioningJob.class);

    private final RoomRepository roomRepository;
    private final LiveQueueSessionProvisioningService provisioningService;

    public LiveQueueSessionProvisioningJob(
            RoomRepository roomRepository,
            LiveQueueSessionProvisioningService provisioningService
    ) {
        this.roomRepository = roomRepository;
        this.provisioningService = provisioningService;
    }

    @Scheduled(
            fixedDelayString = "${app.live-queue.provisioning-delay-ms:30000}",
            initialDelayString = "${app.live-queue.provisioning-initial-delay-ms:1000}"
    )
    public void provisionMissingSessions() {
        List<Long> roomIds = roomRepository.findIdsRequiringLiveQueueSession(
                RoomStatus.PUBLISHED,
                ReservationMode.LIVE_QUEUE,
                PageRequest.of(0, 100)
        );
        for (Long roomId : roomIds) {
            try {
                provisioningService.ensureAutomaticSession(roomId);
            } catch (RuntimeException exception) {
                logger.error("Live queue session provisioning failed: roomId={}", roomId, exception);
            }
        }
    }
}
