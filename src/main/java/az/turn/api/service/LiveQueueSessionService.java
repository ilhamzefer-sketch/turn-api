package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LiveQueueSessionService {
    private static final List<LiveQueueEntryStatus> ACTIVE_STATUSES = List.of(
            LiveQueueEntryStatus.WAITING,
            LiveQueueEntryStatus.CURRENT,
            LiveQueueEntryStatus.SKIPPED
    );

    private final RoomRepository roomRepository;
    private final LiveQueueSessionRepository sessionRepository;
    private final LiveQueueEntryRepository entryRepository;
    private final ProviderAccessService accessService;
    private final LiveQueueAvailabilityService availabilityService;
    private final LiveQueueSessionFactory sessionFactory;
    private final LiveQueueMapper mapper;
    private final Clock clock;

    public LiveQueueSessionService(
            RoomRepository roomRepository,
            LiveQueueSessionRepository sessionRepository,
            LiveQueueEntryRepository entryRepository,
            ProviderAccessService accessService,
            LiveQueueAvailabilityService availabilityService,
            LiveQueueSessionFactory sessionFactory,
            LiveQueueMapper mapper,
            Clock clock
    ) {
        this.roomRepository = roomRepository;
        this.sessionRepository = sessionRepository;
        this.entryRepository = entryRepository;
        this.accessService = accessService;
        this.availabilityService = availabilityService;
        this.sessionFactory = sessionFactory;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public LiveQueueSessionDto open(long roomId, long userId) {
        RoomEntity room = requireOperableRoom(roomId, userId);
        LiveQueueSessionEntity session = sessionRepository.findOpenByRoomIdForUpdate(roomId).orElse(null);
        if (session == null) {
            session = sessionRepository.save(sessionFactory.create(room, LiveQueueAcceptanceOverride.FORCE_OPEN));
        } else {
            session.setAcceptanceOverride(LiveQueueAcceptanceOverride.FORCE_OPEN);
            session = sessionRepository.save(session);
        }
        return operatorDto(session);
    }

    @Transactional
    public LiveQueueSessionDto closeAcceptance(long roomId, long userId) {
        requireOperableRoom(roomId, userId);
        LiveQueueSessionEntity session = requireOpenSessionForUpdate(roomId);
        session.setAcceptanceOverride(LiveQueueAcceptanceOverride.FORCE_CLOSED);
        return operatorDto(sessionRepository.save(session));
    }

    @Transactional
    public LiveQueueSessionDto useAutomaticAcceptance(long roomId, long userId) {
        requireOperableRoom(roomId, userId);
        LiveQueueSessionEntity session = requireOpenSessionForUpdate(roomId);
        session.setAcceptanceOverride(LiveQueueAcceptanceOverride.AUTO);
        return operatorDto(sessionRepository.save(session));
    }

    @Transactional
    public LiveQueueSessionDto reset(long roomId, long userId) {
        requireOperableRoom(roomId, userId);
        LiveQueueSessionEntity current = requireOpenSessionForUpdate(roomId);
        LiveQueueSessionEntity replacement = resetLocked(current);
        return operatorDto(replacement);
    }

    @Transactional
    public void resetDue(long sessionId) {
        LiveQueueSessionEntity session = sessionRepository.findByIdForUpdate(sessionId).orElse(null);
        if (session == null || session.getStatus() != LiveQueueSessionStatus.OPEN
                || session.getNextResetAt().isAfter(LocalDateTime.now(clock))) {
            return;
        }
        resetLocked(session);
    }

    @Transactional(readOnly = true)
    public LiveQueueSessionDto getOperator(long roomId, long userId) {
        accessService.requireRoomViewer(roomId, userId);
        LiveQueueSessionEntity session = sessionRepository.findByRoomIdAndOpenSlot(roomId, 1)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Açıq canlı növbə sessiyası yoxdur."));
        return operatorDto(session);
    }

    @Transactional(readOnly = true)
    public LiveQueuePublicDto getPublic(long roomId) {
        RoomEntity room = requirePublicRoom(roomId);
        LiveQueueSessionEntity session = sessionRepository.findByRoomIdAndOpenSlot(roomId, 1).orElse(null);
        List<LiveQueueEntryEntity> entries = session == null
                ? List.of()
                : entryRepository.findBySessionIdOrderByQueuePositionAsc(session.getId());
        return mapper.toPublicDto(
                room,
                session,
                entries,
                isAccepting(session),
                availabilityService.nextOpeningAt(room)
        );
    }

    LiveQueueSessionEntity requireOpenSessionForUpdate(long roomId) {
        return sessionRepository.findOpenByRoomIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Canlı növbə əvvəlcə açılmalıdır."));
    }

    RoomEntity requirePublicRoom(long roomId) {
        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Otaq tapılmadı."));
        if (room.getStatus() != RoomStatus.PUBLISHED || room.getReservationMode() != ReservationMode.LIVE_QUEUE
                || room.getVisibility() == RoomVisibility.PRIVATE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Açıq canlı növbə tapılmadı.");
        }
        return room;
    }

    boolean isAccepting(LiveQueueSessionEntity session) {
        if (session == null || session.getStatus() != LiveQueueSessionStatus.OPEN
                || !session.getRoom().isLiveQueueAcceptingNewEntries()) {
            return false;
        }
        return switch (session.getAcceptanceOverride()) {
            case FORCE_OPEN -> true;
            case FORCE_CLOSED -> false;
            case AUTO -> availabilityService.isAvailableNow(session.getRoom());
        };
    }

    LiveQueueSessionDto operatorDto(LiveQueueSessionEntity session) {
        List<LiveQueueEntryEntity> entries = entryRepository.findBySessionIdOrderByQueuePositionAsc(session.getId());
        return mapper.toOperatorDto(
                session,
                entries,
                isAccepting(session),
                availabilityService.nextOpeningAt(session.getRoom())
        );
    }

    private RoomEntity requireOperableRoom(long roomId, long userId) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        if (room.getStatus() != RoomStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yalnız yayımlanmış otaqda canlı növbə açıla bilər.");
        }
        if (room.getReservationMode() != ReservationMode.LIVE_QUEUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu otaq canlı növbə rejimində deyil.");
        }
        if (room.getLiveQueueResetPolicy() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Canlı növbənin reset qaydası tamamlanmayıb.");
        }
        return room;
    }

    private LiveQueueSessionEntity resetLocked(LiveQueueSessionEntity current) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<LiveQueueEntryEntity> activeEntries = entryRepository
                .findBySessionIdAndStatusInOrderByQueuePositionAsc(current.getId(), ACTIVE_STATUSES);
        activeEntries.forEach(entry -> {
            entry.setStatus(LiveQueueEntryStatus.RESET);
            entry.setActiveIdentityKey(null);
            entry.setCurrentSlot(null);
            entry.setRemovedAt(now);
        });
        entryRepository.saveAll(activeEntries);
        current.setStatus(LiveQueueSessionStatus.CLOSED);
        current.setOpenSlot(null);
        current.setClosedAt(now);
        LiveQueueAcceptanceOverride nextOverride = current.getAcceptanceOverride() == LiveQueueAcceptanceOverride.FORCE_CLOSED
                ? LiveQueueAcceptanceOverride.FORCE_CLOSED
                : LiveQueueAcceptanceOverride.AUTO;
        sessionRepository.saveAndFlush(current);
        return sessionRepository.save(sessionFactory.create(current.getRoom(), nextOverride));
    }
}
