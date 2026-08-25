package az.turn.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmsInvitationListenerTests {
    @Test
    void sendsBusinessAndRoomInvitationMessages() {
        SmsSender sender = mock(SmsSender.class);
        SmsInvitationListener listener = new SmsInvitationListener(sender);

        listener.sendBusinessInvitation(new BusinessInvitationCreatedEvent(1L, "+994501234567", "Studiya"));
        listener.sendRoomInvitation(new RoomInvitationCreatedEvent(2L, "+994507654321", "Qəbul otağı"));

        verify(sender).send(eq("+994501234567"), contains("Studiya"));
        verify(sender).send(eq("+994507654321"), contains("Qəbul otağı"));
    }

    @Test
    void gatewayFailureDoesNotRollBackCommittedInvitation() {
        SmsSender sender = mock(SmsSender.class);
        doThrow(new SmsDeliveryException("gateway unavailable"))
                .when(sender).send(eq("+994501234567"), anyString());
        SmsInvitationListener listener = new SmsInvitationListener(sender);

        assertDoesNotThrow(() -> listener.sendBusinessInvitation(
                new BusinessInvitationCreatedEvent(1L, "+994501234567", "message")
        ));
    }
}
