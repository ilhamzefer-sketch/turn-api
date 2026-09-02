package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class QrCredentialService {
    private final QrCredentialRepository qrCredentialRepository;
    private final ProviderAccessService accessService;
    private final SecureTokenService tokenService;
    private final Clock clock;

    public QrCredentialService(
            QrCredentialRepository qrCredentialRepository,
            ProviderAccessService accessService,
            SecureTokenService tokenService,
            Clock clock
    ) {
        this.qrCredentialRepository = qrCredentialRepository;
        this.accessService = accessService;
        this.tokenService = tokenService;
        this.clock = clock;
    }

    @Transactional
    public QrCredentialDto create(long roomId, long userId) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        UserEntity creator = accessService.requireActiveUser(userId);
        return createCredential(room, creator, null);
    }

    @Transactional
    public List<QrCredentialDto> list(long roomId, long userId) {
        accessService.requireRoomViewer(roomId, userId);
        return qrCredentialRepository.findByRoomIdOrderByCreatedAtDesc(roomId)
                .stream()
                .map(this::ensurePublicToken)
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void revoke(long roomId, long credentialId, long userId) {
        accessService.requireEditableRoom(roomId, userId);
        QrCredentialEntity credential = find(roomId, credentialId);
        if (!credential.isActive()) return;
        credential.setActive(false);
        credential.setRevokedAt(LocalDateTime.now(clock));
        qrCredentialRepository.save(credential);
    }

    @Transactional
    public QrCredentialDto regenerate(long roomId, long credentialId, long userId) {
        RoomEntity room = accessService.requireEditableRoom(roomId, userId);
        UserEntity creator = accessService.requireActiveUser(userId);
        QrCredentialEntity current = find(roomId, credentialId);
        if (current.isActive()) {
            current.setActive(false);
            current.setRevokedAt(LocalDateTime.now(clock));
            qrCredentialRepository.save(current);
        }
        return createCredential(room, creator, current.getPosterTitle());
    }

    @Transactional
    public QrCredentialDto updatePosterTitle(
            long roomId,
            long credentialId,
            long userId,
            QrPosterTitleUpdateDto request
    ) {
        accessService.requireEditableRoom(roomId, userId);
        QrCredentialEntity credential = find(roomId, credentialId);
        credential.setPosterTitle(normalizePosterTitle(request.posterTitle()));
        return toDto(qrCredentialRepository.save(credential));
    }

    @Transactional
    public QrPosterSpecification posterData(long roomId, long credentialId, long userId, String publicBaseUrl) {
        RoomEntity room = accessService.requireRoomViewer(roomId, userId);
        QrCredentialEntity credential = ensurePublicToken(find(roomId, credentialId));
        if (!credential.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ləğv edilmiş QR kod üçün afişa yaradıla bilməz.");
        }
        String posterTitle = credential.getPosterTitle() == null ? room.getName() : credential.getPosterTitle();
        String baseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return new QrPosterSpecification(
                credential.getId(),
                posterTitle,
                baseUrl + "/q/" + credential.getPublicToken(),
                room.getReservationMode(),
                room.getRoomNumberOrCode(),
                room.getDefaultSlotDurationMinutes(),
                room.getDescription()
        );
    }

    @Transactional(readOnly = true)
    public RoomEntity resolveActiveRoom(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "QR kod tapılmadı.");
        }
        return qrCredentialRepository.findActiveByCurrentOrLegacyTokenHash(tokenService.hash(token.trim()))
                .map(QrCredentialEntity::getRoom)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR kod tapılmadı və ya ləğv edilib."));
    }

    private QrCredentialDto createCredential(RoomEntity room, UserEntity creator, String posterTitle) {
        String rawToken = tokenService.generate();
        QrCredentialEntity credential = new QrCredentialEntity();
        credential.setRoom(room);
        credential.setTokenHash(tokenService.hash(rawToken));
        credential.setPublicToken(rawToken);
        credential.setType(QrCredentialType.PERMANENT_ROOM);
        credential.setActive(true);
        credential.setCreatedByUser(creator);
        credential.setPosterTitle(posterTitle);
        return toDto(qrCredentialRepository.save(credential));
    }

    private QrCredentialEntity find(long roomId, long credentialId) {
        return qrCredentialRepository.findByIdAndRoomId(credentialId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR kod tapılmadı."));
    }

    private QrCredentialEntity ensurePublicToken(QrCredentialEntity credential) {
        if (!credential.isActive() || (credential.getPublicToken() != null && !credential.getPublicToken().isBlank())) {
            return credential;
        }

        String rawToken = tokenService.generate();
        credential.setLegacyTokenHash(credential.getTokenHash());
        credential.setTokenHash(tokenService.hash(rawToken));
        credential.setPublicToken(rawToken);
        return qrCredentialRepository.save(credential);
    }

    private QrCredentialDto toDto(QrCredentialEntity value) {
        return new QrCredentialDto(
                value.getId(),
                value.getRoom().getId(),
                value.getType(),
                value.isActive(),
                value.getPublicToken(),
                value.getPosterTitle(),
                value.getCreatedAt(),
                value.getRevokedAt()
        );
    }

    private String normalizePosterTitle(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().replaceAll("\\s+", " ");
    }
}
