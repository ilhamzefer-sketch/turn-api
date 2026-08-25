package az.turn.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class SessionRetentionJob {
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;
    private final long retentionDays;

    public SessionRetentionJob(
            RefreshTokenRepository refreshTokenRepository,
            Clock clock,
            @Value("${app.security.session.retention-days:90}") long retentionDays
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
        if (retentionDays <= 0) {
            throw new IllegalStateException("Session retention days must be positive");
        }
        this.retentionDays = retentionDays;
    }

    @Scheduled(
            fixedDelayString = "${app.security.session.cleanup-delay-ms:86400000}",
            initialDelayString = "${app.security.session.cleanup-initial-delay-ms:60000}"
    )
    @Transactional
    public void clean() {
        LocalDateTime now = LocalDateTime.now(clock);
        refreshTokenRepository.revokeExpired(now, SessionRevocationReason.EXPIRED);
        refreshTokenRepository.deleteByRevokedTrueAndRevokedAtBefore(now.minusDays(retentionDays));
    }
}
