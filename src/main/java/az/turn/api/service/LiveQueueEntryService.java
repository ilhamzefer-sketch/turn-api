package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class LiveQueueEntryService {
    private static final List<LiveQueueEntryStatus> ACTIVE_STATUSES = List.of(
            LiveQueueEntryStatus.WAITING,
            LiveQueueEntryStatus.CURRENT,
            LiveQueueEntryStatus.SKIPPED
    );

    private final LiveQueueSessionService sessionService;
    private final LiveQueueEntryRepository entryRepository;
    private final UserRepository userRepository;
    private final GuestContactService guestContactService;
    private final ProviderAccessService accessService;
    private final SecureTokenService tokenService;
    private final LiveQueueMapper mapper;

    public LiveQueueEntryService(
            LiveQueueSessionService sessionService,
            LiveQueueEntryRepository entryRepository,
            UserRepository userRepository,
            GuestContactService guestContactService,
            ProviderAccessService accessService,
            SecureTokenService tokenService,
            LiveQueueMapper mapper
    ) {
        this.sessionService = sessionService;
        this.entryRepository = entryRepository;
        this.userRepository = userRepository;
        this.guestContactService = guestContactService;
        this.accessService = accessService;
        this.tokenService = tokenService;
        this.mapper = mapper;
    }

    @Transactional
    public LiveQueueJoinResponseDto joinGuest(
            long roomId,
            LiveQueueJoinRequestDto request,
            LiveQueueEntrySource source
    ) {
        sessionService.requirePublicRoom(roomId);
        LiveQueueSessionEntity session = requireAcceptingSession(roomId);
        GuestContactEntity contact = guestContactService.resolve(request.displayName(), request.phone());
        String identityKey = guestContactService.identityKey(contact.getNormalizedPhone());
        LiveQueueEntryEntity existing = findExisting(session, identityKey);
        if (existing != null) return joinDto(existing, session);
        LiveQueueEntryEntity entry = baseEntry(session, identityKey, source, request.displayName().trim());
        entry.setGuestContact(contact);
        return joinDto(entryRepository.save(entry), session);
    }

    @Transactional
    public LiveQueueJoinResponseDto joinUser(long roomId, long userId) {
        sessionService.requirePublicRoom(roomId);
        LiveQueueSessionEntity session = requireAcceptingSession(roomId);
        UserEntity user = accessService.requireActiveUser(userId);
        String identityKey = guestContactService.identityKey(user.getNormalizedPhone());
        LiveQueueEntryEntity existing = findExisting(session, identityKey);
        if (existing != null) return joinDto(existing, session);
        LiveQueueEntryEntity entry = baseEntry(
                session,
                identityKey,
                LiveQueueEntrySource.WEB,
                user.getFirstName() + " " + user.getLastName()
        );
        entry.setUser(user);
        return joinDto(entryRepository.save(entry), session);
    }

    @Transactional
    public LiveQueueEntryDto addManual(
            long roomId,
            long userId,
            LiveQueueManualEntryRequestDto request
    ) {
        accessService.requireEditableRoom(roomId, userId);
        if (request.source() == LiveQueueEntrySource.WEB || request.source() == LiveQueueEntrySource.QR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manual giriş üçün owner mənbəyi seçilməlidir.");
        }
        LiveQueueSessionEntity session = sessionService.requireOpenSessionForUpdate(roomId);
        GuestContactEntity contact = guestContactService.resolve(request.displayName(), request.phone());
        String identityKey = guestContactService.identityKey(contact.getNormalizedPhone());
        LiveQueueEntryEntity existing = findExisting(session, identityKey);
        if (existing != null) return mapper.toEntryDto(existing);
        UserEntity creator = accessService.requireActiveUser(userId);
        LiveQueueEntryEntity entry = baseEntry(
                session,
                identityKey,
                request.source(),
                request.displayName().trim()
        );
        entry.setGuestContact(contact);
        entry.setCreatedByUser(creator);
        entry.setInternalNote(normalizeOptional(request.internalNote()));
        return mapper.toEntryDto(entryRepository.save(entry));
    }

    @Transactional(readOnly = true)
    public LiveQueueParticipantStatusDto getParticipantStatus(String publicReference) {
        LiveQueueEntryEntity entry = entryRepository.findByPublicReference(publicReference)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Növbə iştirakı tapılmadı."));
        LiveQueueSessionEntity session = entry.getSession();
        List<LiveQueueEntryEntity> activeEntries = activeEntries(session.getId());
        return mapper.toParticipantStatusDto(
                entry,
                activeEntries,
                sessionService.isAccepting(session),
                entry.getRoom().getDefaultSlotDurationMinutes()
        );
    }

    @Transactional(readOnly = true)
    public List<LiveQueueHistoryItemDto> getUserHistory(long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "İstifadəçi tapılmadı.");
        }
        return entryRepository.findByUserIdOrGuestContactLinkedUserIdOrderByCreatedAtDesc(userId, userId)
                .stream()
                .map(mapper::toHistoryDto)
                .toList();
    }

    private LiveQueueSessionEntity requireAcceptingSession(long roomId) {
        LiveQueueSessionEntity session = sessionService.requireOpenSessionForUpdate(roomId);
        if (!sessionService.isAccepting(session)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Canlı növbə hazırda yeni iştirakçı qəbul etmir.");
        }
        return session;
    }

    private LiveQueueEntryEntity baseEntry(
            LiveQueueSessionEntity session,
            String identityKey,
            LiveQueueEntrySource source,
            String displayName
    ) {
        enforceLimit(session);
        session.setNextPosition(session.getNextPosition() + 1);
        LiveQueueEntryEntity entry = new LiveQueueEntryEntity();
        entry.setSession(session);
        entry.setRoom(session.getRoom());
        entry.setQueuePosition(session.getNextPosition());
        entry.setPublicReference(publicReference());
        entry.setStatus(LiveQueueEntryStatus.WAITING);
        entry.setSource(source);
        entry.setActiveIdentityKey(identityKey);
        entry.setPrivateDisplayName(displayName);
        return entry;
    }

    private LiveQueueEntryEntity findExisting(LiveQueueSessionEntity session, String identityKey) {
        return entryRepository.findBySessionIdAndActiveIdentityKey(session.getId(), identityKey).orElse(null);
    }

    private LiveQueueJoinResponseDto joinDto(LiveQueueEntryEntity entry, LiveQueueSessionEntity session) {
        return mapper.toJoinDto(
                entry,
                activeEntries(session.getId()),
                sessionService.isAccepting(session),
                session.getRoom().getDefaultSlotDurationMinutes()
        );
    }

    private List<LiveQueueEntryEntity> activeEntries(long sessionId) {
        return entryRepository.findBySessionIdAndStatusInOrderByQueuePositionAsc(sessionId, ACTIVE_STATUSES);
    }

    private void enforceLimit(LiveQueueSessionEntity session) {
        Integer limit = session.getRoom().getLiveQueueMaxParticipants();
        if (limit != null && entryRepository.countBySessionIdAndStatusIn(session.getId(), ACTIVE_STATUSES) >= limit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Canlı növbənin iştirakçı limiti dolub.");
        }
    }

    private String publicReference() {
        return "Q-" + tokenService.generate().substring(0, 12).toUpperCase();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
