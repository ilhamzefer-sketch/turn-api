package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LiveQueueOperationService {
    private static final List<LiveQueueEntryStatus> ACTIVE_STATUSES = List.of(
            LiveQueueEntryStatus.WAITING,
            LiveQueueEntryStatus.CURRENT,
            LiveQueueEntryStatus.SKIPPED
    );

    private final LiveQueueSessionService sessionService;
    private final LiveQueueEntryRepository entryRepository;
    private final ProviderAccessService accessService;
    private final GuestContactService guestContactService;
    private final LiveQueueMapper mapper;
    private final Clock clock;

    public LiveQueueOperationService(
            LiveQueueSessionService sessionService,
            LiveQueueEntryRepository entryRepository,
            ProviderAccessService accessService,
            GuestContactService guestContactService,
            LiveQueueMapper mapper,
            Clock clock
    ) {
        this.sessionService = sessionService;
        this.entryRepository = entryRepository;
        this.accessService = accessService;
        this.guestContactService = guestContactService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public LiveQueueSessionDto callNext(long roomId, long userId) {
        requireOperator(roomId, userId);
        LiveQueueSessionEntity session = sessionService.requireOpenSessionForUpdate(roomId);
        if (entryRepository.findBySessionIdAndStatus(session.getId(), LiveQueueEntryStatus.CURRENT).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hazırda xidmət alan iştirakçı tamamlanmalıdır.");
        }
        LiveQueueEntryEntity next = nextWaiting(session.getId());
        setCurrent(next);
        entryRepository.save(next);
        return sessionService.operatorDto(session);
    }

    @Transactional
    public LiveQueueSessionDto completeCurrent(long roomId, long userId) {
        requireOperator(roomId, userId);
        LiveQueueSessionEntity session = sessionService.requireOpenSessionForUpdate(roomId);
        LiveQueueEntryEntity current = entryRepository
                .findBySessionIdAndStatus(session.getId(), LiveQueueEntryStatus.CURRENT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Xidmət alan iştirakçı yoxdur."));
        terminal(current, LiveQueueEntryStatus.COMPLETED);
        current.setCompletedAt(LocalDateTime.now(clock));
        entryRepository.saveAndFlush(current);
        promoteNext(session.getId());
        return sessionService.operatorDto(session);
    }

    @Transactional
    public LiveQueueSessionDto skip(long roomId, long entryId, long userId) {
        requireOperator(roomId, userId);
        LiveQueueSessionEntity session = sessionService.requireOpenSessionForUpdate(roomId);
        LiveQueueEntryEntity entry = requireCurrentSessionEntry(roomId, entryId, session);
        if (entry.getStatus() == LiveQueueEntryStatus.SKIPPED) return sessionService.operatorDto(session);
        requireStatus(entry, LiveQueueEntryStatus.WAITING, LiveQueueEntryStatus.CURRENT);
        boolean wasCurrent = entry.getStatus() == LiveQueueEntryStatus.CURRENT;
        entry.setStatus(LiveQueueEntryStatus.SKIPPED);
        entry.setCurrentSlot(null);
        entryRepository.saveAndFlush(entry);
        if (wasCurrent) promoteNext(session.getId());
        return sessionService.operatorDto(session);
    }

    @Transactional
    public LiveQueueSessionDto restore(long roomId, long entryId, long userId) {
        requireOperator(roomId, userId);
        LiveQueueSessionEntity session = sessionService.requireOpenSessionForUpdate(roomId);
        LiveQueueEntryEntity entry = requireCurrentSessionEntry(roomId, entryId, session);
        requireStatus(entry, LiveQueueEntryStatus.SKIPPED);
        entry.setStatus(LiveQueueEntryStatus.WAITING);
        entryRepository.save(entry);
        return sessionService.operatorDto(session);
    }

    @Transactional
    public LiveQueueSessionDto sendToEnd(long roomId, long entryId, long userId) {
        requireOperator(roomId, userId);
        LiveQueueSessionEntity session = sessionService.requireOpenSessionForUpdate(roomId);
        LiveQueueEntryEntity entry = requireCurrentSessionEntry(roomId, entryId, session);
        requireStatus(entry, LiveQueueEntryStatus.WAITING, LiveQueueEntryStatus.SKIPPED);
        session.setNextPosition(session.getNextPosition() + 1);
        entry.setQueuePosition(session.getNextPosition());
        entry.setStatus(LiveQueueEntryStatus.WAITING);
        entryRepository.save(entry);
        return sessionService.operatorDto(session);
    }

    @Transactional
    public LiveQueueSessionDto remove(long roomId, long entryId, long userId) {
        requireOperator(roomId, userId);
        LiveQueueSessionEntity session = sessionService.requireOpenSessionForUpdate(roomId);
        LiveQueueEntryEntity entry = requireCurrentSessionEntry(roomId, entryId, session);
        requireStatus(entry, LiveQueueEntryStatus.WAITING, LiveQueueEntryStatus.CURRENT, LiveQueueEntryStatus.SKIPPED);
        boolean wasCurrent = entry.getStatus() == LiveQueueEntryStatus.CURRENT;
        terminal(entry, LiveQueueEntryStatus.REMOVED);
        entry.setRemovedAt(LocalDateTime.now(clock));
        entryRepository.saveAndFlush(entry);
        if (wasCurrent) promoteNext(session.getId());
        return sessionService.operatorDto(session);
    }

    @Transactional
    public LiveQueueEntryDto updateManual(
            long roomId,
            long entryId,
            long userId,
            LiveQueueEntryUpdateRequestDto request
    ) {
        requireOperator(roomId, userId);
        LiveQueueSessionEntity session = sessionService.requireOpenSessionForUpdate(roomId);
        LiveQueueEntryEntity entry = requireCurrentSessionEntry(roomId, entryId, session);
        if (entry.getGuestContact() == null || entry.getCreatedByUser() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yalnız owner tərəfindən yaradılmış guest girişi dəyişdirilə bilər.");
        }
        GuestContactEntity contact = guestContactService.resolve(request.displayName(), request.phone());
        String identityKey = guestContactService.identityKey(contact.getNormalizedPhone());
        LiveQueueEntryEntity duplicate = entryRepository
                .findBySessionIdAndActiveIdentityKey(session.getId(), identityKey)
                .orElse(null);
        if (duplicate != null && !duplicate.getId().equals(entry.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu telefon artıq canlı növbədədir.");
        }
        entry.setGuestContact(contact);
        entry.setPrivateDisplayName(request.displayName().trim());
        entry.setInternalNote(request.internalNote() == null || request.internalNote().isBlank()
                ? null
                : request.internalNote().trim());
        if (ACTIVE_STATUSES.contains(entry.getStatus())) entry.setActiveIdentityKey(identityKey);
        return mapper.toEntryDto(entryRepository.save(entry));
    }

    private void requireOperator(long roomId, long userId) {
        accessService.requireEditableRoom(roomId, userId);
    }

    private LiveQueueEntryEntity requireCurrentSessionEntry(
            long roomId,
            long entryId,
            LiveQueueSessionEntity session
    ) {
        LiveQueueEntryEntity entry = entryRepository.findByIdAndSessionRoomId(entryId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Növbə iştirakı tapılmadı."));
        if (!entry.getSession().getId().equals(session.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "İştirak əvvəlki canlı növbə sessiyasına aiddir.");
        }
        return entry;
    }

    private LiveQueueEntryEntity nextWaiting(long sessionId) {
        return entryRepository.findFirstBySessionIdAndStatusOrderByQueuePositionAsc(
                        sessionId,
                        LiveQueueEntryStatus.WAITING
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Gözləyən iştirakçı yoxdur."));
    }

    private void promoteNext(long sessionId) {
        entryRepository.findFirstBySessionIdAndStatusOrderByQueuePositionAsc(
                sessionId,
                LiveQueueEntryStatus.WAITING
        ).ifPresent(entry -> {
            setCurrent(entry);
            entryRepository.save(entry);
        });
    }

    private void setCurrent(LiveQueueEntryEntity entry) {
        entry.setStatus(LiveQueueEntryStatus.CURRENT);
        entry.setCurrentSlot(1);
    }

    private void terminal(LiveQueueEntryEntity entry, LiveQueueEntryStatus status) {
        entry.setStatus(status);
        entry.setActiveIdentityKey(null);
        entry.setCurrentSlot(null);
    }

    private void requireStatus(LiveQueueEntryEntity entry, LiveQueueEntryStatus... allowed) {
        if (!List.of(allowed).contains(entry.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu əməliyyat iştirakçının cari vəziyyətinə uyğun deyil.");
        }
    }
}
