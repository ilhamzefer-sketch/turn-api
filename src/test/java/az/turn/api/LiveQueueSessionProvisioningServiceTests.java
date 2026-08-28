package az.turn.api;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveQueueSessionProvisioningServiceTests {
    @Test
    void createsAutomaticSessionForEligiblePublishedRoom() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        LiveQueueSessionRepository sessionRepository = mock(LiveQueueSessionRepository.class);
        LiveQueueSessionFactory sessionFactory = mock(LiveQueueSessionFactory.class);
        RoomEntity room = eligibleRoom();
        LiveQueueSessionEntity created = new LiveQueueSessionEntity();
        when(roomRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomIdAndOpenSlot(7L, 1)).thenReturn(Optional.empty());
        when(sessionFactory.create(room, LiveQueueAcceptanceOverride.AUTO)).thenReturn(created);
        when(sessionRepository.save(created)).thenReturn(created);
        LiveQueueSessionProvisioningService service = new LiveQueueSessionProvisioningService(
                roomRepository,
                sessionRepository,
                sessionFactory,
                new RoomDefaults()
        );

        LiveQueueSessionEntity result = service.ensureAutomaticSession(7L);

        assertThat(result).isSameAs(created);
        verify(sessionFactory).create(room, LiveQueueAcceptanceOverride.AUTO);
        verify(sessionRepository).save(created);
    }

    @Test
    void reusesExistingSessionWithoutCreatingAnother() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        LiveQueueSessionRepository sessionRepository = mock(LiveQueueSessionRepository.class);
        LiveQueueSessionFactory sessionFactory = mock(LiveQueueSessionFactory.class);
        RoomEntity room = eligibleRoom();
        LiveQueueSessionEntity existing = new LiveQueueSessionEntity();
        when(roomRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomIdAndOpenSlot(7L, 1)).thenReturn(Optional.of(existing));
        LiveQueueSessionProvisioningService service = new LiveQueueSessionProvisioningService(
                roomRepository,
                sessionRepository,
                sessionFactory,
                new RoomDefaults()
        );

        LiveQueueSessionEntity result = service.ensureAutomaticSession(7L);

        assertThat(result).isSameAs(existing);
        verify(sessionFactory, never()).create(room, LiveQueueAcceptanceOverride.AUTO);
    }

    @Test
    void resumesAutomaticAcceptanceWhenRoomIsPublishedAgain() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        LiveQueueSessionRepository sessionRepository = mock(LiveQueueSessionRepository.class);
        LiveQueueSessionFactory sessionFactory = mock(LiveQueueSessionFactory.class);
        RoomEntity room = eligibleRoom();
        LiveQueueSessionEntity existing = new LiveQueueSessionEntity();
        existing.setAcceptanceOverride(LiveQueueAcceptanceOverride.FORCE_CLOSED);
        when(roomRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomIdAndOpenSlot(7L, 1)).thenReturn(Optional.of(existing));
        when(sessionRepository.save(existing)).thenReturn(existing);
        LiveQueueSessionProvisioningService service = new LiveQueueSessionProvisioningService(
                roomRepository,
                sessionRepository,
                sessionFactory,
                new RoomDefaults()
        );

        LiveQueueSessionEntity result = service.resumeAutomaticSession(7L);

        assertThat(result).isSameAs(existing);
        assertThat(existing.getAcceptanceOverride()).isEqualTo(LiveQueueAcceptanceOverride.AUTO);
        verify(sessionRepository).save(existing);
    }

    @Test
    void repairsMissingResetConfigurationBeforeCreatingSession() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        LiveQueueSessionRepository sessionRepository = mock(LiveQueueSessionRepository.class);
        LiveQueueSessionFactory sessionFactory = mock(LiveQueueSessionFactory.class);
        RoomEntity room = eligibleRoom();
        room.setLiveQueueResetPolicy(null);
        room.setLiveQueueResetLocalTime(null);
        LiveQueueSessionEntity created = new LiveQueueSessionEntity();
        when(roomRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomIdAndOpenSlot(7L, 1)).thenReturn(Optional.empty());
        when(sessionFactory.create(room, LiveQueueAcceptanceOverride.AUTO)).thenReturn(created);
        when(sessionRepository.save(created)).thenReturn(created);
        LiveQueueSessionProvisioningService service = new LiveQueueSessionProvisioningService(
                roomRepository,
                sessionRepository,
                sessionFactory,
                new RoomDefaults()
        );

        LiveQueueSessionEntity result = service.ensureAutomaticSession(7L);

        assertThat(result).isSameAs(created);
        assertThat(room.getLiveQueueResetPolicy()).isEqualTo(LiveQueueResetPolicy.DAILY_AT_TIME);
        assertThat(room.getLiveQueueResetLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        verify(sessionFactory).create(room, LiveQueueAcceptanceOverride.AUTO);
    }

    private RoomEntity eligibleRoom() {
        RoomEntity room = new RoomEntity();
        room.setStatus(RoomStatus.PUBLISHED);
        room.setReservationMode(ReservationMode.LIVE_QUEUE);
        room.setLiveQueueResetPolicy(LiveQueueResetPolicy.DAILY_AT_TIME);
        room.setLiveQueueResetLocalTime(LocalTime.MIDNIGHT);
        return room;
    }
}
