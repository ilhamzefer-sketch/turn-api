package az.turn.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SmsInvitationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmsInvitationListener.class);

    private final SmsSender smsSender;

    public SmsInvitationListener(SmsSender smsSender) {
        this.smsSender = smsSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("smsTaskExecutor")
    public void sendBusinessInvitation(BusinessInvitationCreatedEvent event) {
        deliver(
                event.membershipId(),
                event.phone(),
                "NövbəTime: " + event.businessName() + " sizi komandaya dəvət edib. Hesabınıza daxil olaraq dəvəti cavablandırın."
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("smsTaskExecutor")
    public void sendRoomInvitation(RoomInvitationCreatedEvent event) {
        deliver(
                event.assignmentId(),
                event.phone(),
                "NövbəTime: " + event.roomName() + " otağını idarə etmək üçün dəvət almısınız. Hesabınıza daxil olaraq dəvəti cavablandırın."
        );
    }

    private void deliver(long invitationId, String phone, String message) {
        try {
            smsSender.send(phone, message);
        } catch (RuntimeException exception) {
            LOGGER.warn("SMS invitation delivery failed for invitation {}", invitationId, exception);
        }
    }
}
