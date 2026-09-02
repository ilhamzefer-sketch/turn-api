package az.turn.api;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class SecureAttachmentService {
    private final UserRepository userRepository;
    private final SecureAttachmentRepository attachmentRepository;
    private final SecureUploadInputReader inputReader;
    private final SecureReceiptNormalizer normalizer;
    private final MalwareScanner malwareScanner;
    private final PrivateAttachmentStorage storage;
    private final SecureStorageKeyGenerator storageKeyGenerator;
    private final Clock clock;

    public SecureAttachmentService(
            UserRepository userRepository,
            SecureAttachmentRepository attachmentRepository,
            SecureUploadInputReader inputReader,
            SecureReceiptNormalizer normalizer,
            MalwareScanner malwareScanner,
            PrivateAttachmentStorage storage,
            SecureStorageKeyGenerator storageKeyGenerator,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.inputReader = inputReader;
        this.normalizer = normalizer;
        this.malwareScanner = malwareScanner;
        this.storage = storage;
        this.storageKeyGenerator = storageKeyGenerator;
        this.clock = clock;
    }

    public StoredSecureAttachment storeImage(
            long ownerUserId,
            SecureAttachmentPurpose purpose,
            SecureUploadCommand command
    ) {
        return store(ownerUserId, purpose, command, false);
    }

    public StoredSecureAttachment storePaymentReceipt(
            long ownerUserId,
            SecureUploadCommand command
    ) {
        return store(ownerUserId, SecureAttachmentPurpose.PAYMENT_RECEIPT, command, true);
    }

    private StoredSecureAttachment store(
            long ownerUserId,
            SecureAttachmentPurpose purpose,
            SecureUploadCommand command,
            boolean allowPdf
    ) {
        UserEntity owner = userRepository.findById(ownerUserId)
                .orElseThrow(() -> new SecureUploadException(
                        SecureUploadFailure.OWNER_NOT_FOUND,
                        "İstifadəçi tapılmadı."
                ));
        SecureUploadSource source = inputReader.read(command);
        requireClean(malwareScanner.scan(source.bytes()));
        NormalizedAttachment file = allowPdf
                ? normalizer.normalizeReceipt(source)
                : normalizer.normalizeImage(source);
        String storageKey = storageKeyGenerator.generate(file.fileExtension());
        LocalDateTime now = LocalDateTime.now(clock);
        storage.store(storageKey, file.bytes());
        try {
            SecureAttachmentEntity attachment = attachmentRepository.saveAndFlush(
                    new SecureAttachmentEntity(
                            owner,
                            purpose,
                            storageKey,
                            file.originalFilename(),
                            file.mediaType(),
                            file.fileExtension(),
                            file.bytes().length,
                            file.widthPixels(),
                            file.heightPixels(),
                            file.sha256(),
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
