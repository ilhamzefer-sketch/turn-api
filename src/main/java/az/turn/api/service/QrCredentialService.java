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
        return createCredential(room, creator);
    }

    @Transactional(readOnly = true)
    public List<QrCredentialDto> list(long roomId, long userId) {
        accessService.requireRoomViewer(roomId, userId);
        return qrCredentialRepository.findByRoomIdOrderByCreatedAtDesc(roomId)
                .stream()
                .map(value -> toDto(value, null))
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
        return createCredential(room, creator);
    }

    @Transactional(readOnly = true)
    public RoomEntity resolveActiveRoom(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "QR kod tapılmadı.");
        }
        return qrCredentialRepository.findByTokenHashAndActiveTrue(tokenService.hash(token.trim()))
                .map(QrCredentialEntity::getRoom)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR kod tapılmadı və ya ləğv edilib."));
    }

    private QrCredentialDto createCredential(RoomEntity room, UserEntity creator) {
        String rawToken = tokenService.generate();
        QrCredentialEntity credential = new QrCredentialEntity();
        credential.setRoom(room);
        credential.setTokenHash(tokenService.hash(rawToken));
        credential.setType(QrCredentialType.PERMANENT_ROOM);
        credential.setActive(true);
        credential.setCreatedByUser(creator);
        return toDto(qrCredentialRepository.save(credential), rawToken);
    }

    private QrCredentialEntity find(long roomId, long credentialId) {
        return qrCredentialRepository.findByIdAndRoomId(credentialId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR kod tapılmadı."));
    }

    private QrCredentialDto toDto(QrCredentialEntity value, String token) {
        return new QrCredentialDto(
                value.getId(),
                value.getRoom().getId(),
                value.getType(),
                value.isActive(),
                token,
                value.getCreatedAt(),
                value.getRevokedAt()
        );
    }
}
