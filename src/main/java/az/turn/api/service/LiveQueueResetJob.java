package az.turn.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.live-queue.reset-enabled", havingValue = "true", matchIfMissing = true)
public class LiveQueueResetJob {
    private static final Logger logger = LoggerFactory.getLogger(LiveQueueResetJob.class);

    private final LiveQueueSessionRepository sessionRepository;
    private final LiveQueueSessionService sessionService;
    private final Clock clock;

    public LiveQueueResetJob(
            LiveQueueSessionRepository sessionRepository,
            LiveQueueSessionService sessionService,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionService = sessionService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${app.live-queue.reset-delay-ms:30000}",
            initialDelayString = "${app.live-queue.reset-initial-delay-ms:15000}"
    )
    public void resetDueSessions() {
        List<Long> sessionIds = sessionRepository.findDueSessionIds(
                LiveQueueSessionStatus.OPEN,
                LocalDateTime.now(clock),
                PageRequest.of(0, 100)
        );
        for (Long sessionId : sessionIds) {
            try {
                sessionService.resetDue(sessionId);
            } catch (RuntimeException exception) {
                logger.error("Live queue reset failed: sessionId={}", sessionId, exception);
            }
        }
    }
}
