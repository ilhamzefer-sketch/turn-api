package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LiveQueueSessionProvisioningService {
    private final RoomRepository roomRepository;
    private final LiveQueueSessionRepository sessionRepository;
    private final LiveQueueSessionFactory sessionFactory;

    public LiveQueueSessionProvisioningService(
            RoomRepository roomRepository,
            LiveQueueSessionRepository sessionRepository,
            LiveQueueSessionFactory sessionFactory
    ) {
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
        this.sessionFactory = sessionFactory;
    }

    @Transactional
    public LiveQueueSessionEntity ensureAutomaticSession(long roomId) {
        return ensureAutomaticSession(roomId, false);
    }

    @Transactional
    public LiveQueueSessionEntity resumeAutomaticSession(long roomId) {
        return ensureAutomaticSession(roomId, true);
    }

    private LiveQueueSessionEntity ensureAutomaticSession(long roomId, boolean resumeAutomaticAcceptance) {
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElse(null);
        if (!isEligible(room)) return null;
        LiveQueueSessionEntity session = sessionRepository.findByRoomIdAndOpenSlot(roomId, 1)
                .orElseGet(() -> sessionRepository.save(
                        sessionFactory.create(room, LiveQueueAcceptanceOverride.AUTO)
                ));
        if (resumeAutomaticAcceptance
                && session.getAcceptanceOverride() != LiveQueueAcceptanceOverride.AUTO) {
            session.setAcceptanceOverride(LiveQueueAcceptanceOverride.AUTO);
            return sessionRepository.save(session);
        }
        return session;
    }

    private boolean isEligible(RoomEntity room) {
        return room != null
                && room.getStatus() == RoomStatus.PUBLISHED
                && room.getReservationMode() == ReservationMode.LIVE_QUEUE
                && room.getLiveQueueResetPolicy() != null;
    }
}
