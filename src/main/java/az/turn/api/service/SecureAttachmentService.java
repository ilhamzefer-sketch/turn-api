package az.turn.api;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class SecureAttachmentService {
    private final UserRepository userRepository;
    private final SecureAttachmentRepository attachmentRepository;
    private final SecureUploadInputReader inputReader;
    private final SecureImageNormalizer imageNormalizer;
    private final MalwareScanner malwareScanner;
    private final PrivateAttachmentStorage storage;
    private final SecureStorageKeyGenerator storageKeyGenerator;
    private final Clock clock;

    public SecureAttachmentService(
            UserRepository userRepository,
            SecureAttachmentRepository attachmentRepository,
            SecureUploadInputReader inputReader,
            SecureImageNormalizer imageNormalizer,
            MalwareScanner malwareScanner,
            PrivateAttachmentStorage storage,
            SecureStorageKeyGenerator storageKeyGenerator,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.inputReader = inputReader;
        this.imageNormalizer = imageNormalizer;
        this.malwareScanner = malwareScanner;
        this.storage = storage;
        this.storageKeyGenerator = storageKeyGenerator;
        this.clock = clock;
    }

    public StoredSecureAttachment storeImage(
            long ownerUserId,
            SecureAttachmentPurpose purpose,
            SecureImageUploadCommand command
    ) {
        UserEntity owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new SecureUploadException(
                        SecureUploadFailure.OWNER_NOT_FOUND,
                        "İstifadəçi tapılmadı."
                ));
        SecureImageSource source = inputReader.read(command);
        requireClean(malwareScanner.scan(source.bytes()));
        NormalizedImage image = imageNormalizer.normalize(source);
        String filename = imageNormalizer.sanitizeFilename(source.originalFilename());
        String storageKey = storageKeyGenerator.generate(image.fileExtension());
        LocalDateTime now = LocalDateTime.now(clock);
        storage.store(storageKey, image.bytes());
        try {
            SecureAttachmentEntity attachment = attachmentRepository.saveAndFlush(
                    new SecureAttachmentEntity(
                            owner,
                            purpose,
                            storageKey,
                            filename,
                            image.mediaType(),
                            image.fileExtension(),
                            image.bytes().length,
                            image.widthPixels(),
                            image.heightPixels(),
                            image.sha256(),
                            now
                    )
            );
            return map(attachment);
        } catch (RuntimeException exception) {
            removeOrphan(storageKey, exception);
            throw exception;
        }
    }

    private void requireClean(MalwareScanResult result) {
        if (result == MalwareScanResult.INFECTED) {
            throw new SecureUploadException(
                    SecureUploadFailure.MALWARE_DETECTED,
                    "Fayl təhlükəsizlik yoxlamasından keçmədi."
            );
        }
    }

    private void removeOrphan(String storageKey, RuntimeException persistenceFailure) {
        try {
            storage.deleteIfExists(storageKey);
        } catch (RuntimeException cleanupFailure) {
            persistenceFailure.addSuppressed(cleanupFailure);
        }
    }

    private StoredSecureAttachment map(SecureAttachmentEntity attachment) {
        return new StoredSecureAttachment(
                attachment.getId(),
                attachment.getOwnerUser().getId(),
                attachment.getPurpose(),
                attachment.getOriginalFilename(),
                attachment.getMediaType(),
                attachment.getSizeBytes(),
                attachment.getWidthPixels(),
                attachment.getHeightPixels(),
                attachment.getSha256(),
                attachment.getScannedAt()
        );
    }
}
