package az.turn.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.payment.reconciliation-enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationJob {
    private static final Logger logger = LoggerFactory.getLogger(PaymentReconciliationJob.class);
    private final PaymentSessionRepository paymentSessionRepository;
    private final PaymentSessionService paymentSessionService;
    private final SubscriptionPaymentService subscriptionPaymentService;

    public PaymentReconciliationJob(
            PaymentSessionRepository paymentSessionRepository,
            PaymentSessionService paymentSessionService,
            SubscriptionPaymentService subscriptionPaymentService
    ) {
        this.paymentSessionRepository = paymentSessionRepository;
        this.paymentSessionService = paymentSessionService;
        this.subscriptionPaymentService = subscriptionPaymentService;
    }

    @Scheduled(fixedDelayString = "${app.payment.reconciliation-delay-ms:60000}",
            initialDelayString = "${app.payment.reconciliation-initial-delay-ms:30000}")
    public void reconcile() {
        List<PaymentSessionEntity> pendingSessions = paymentSessionRepository
                .findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                        PaymentStatus.PENDING, LocalDateTime.now().minusSeconds(15), PageRequest.of(0, 100));

        for (PaymentSessionEntity session : pendingSessions) {
            try {
                if (session.getPaymentPurpose() == PaymentPurpose.PROVIDER_SUBSCRIPTION) {
                    subscriptionPaymentService.reconcile(session.getId());
                } else {
                    paymentSessionService.reconcilePendingPayment(session.getId());
                }
            } catch (ResponseStatusException exception) {
                logger.warn("Payment reconciliation failed: paymentSessionId={}, status={}",
                        session.getId(), exception.getStatusCode().value());
            } catch (RuntimeException exception) {
                logger.error("Payment reconciliation error: paymentSessionId={}", session.getId(), exception);
            }
        }
    }
}
