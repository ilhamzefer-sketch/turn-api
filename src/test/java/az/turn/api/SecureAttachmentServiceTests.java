package az.turn.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SecureAttachmentServiceTests {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-31T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void storesOnlyNormalizedContentAfterACleanScan() {
        UserRepository userRepository = mock(UserRepository.class);
        SecureAttachmentRepository attachmentRepository = mock(SecureAttachmentRepository.class);
        SecureUploadInputReader inputReader = mock(SecureUploadInputReader.class);
        SecureImageNormalizer normalizer = mock(SecureImageNormalizer.class);
        MalwareScanner scanner = mock(MalwareScanner.class);
        PrivateAttachmentStorage storage = mock(PrivateAttachmentStorage.class);
        SecureStorageKeyGenerator keyGenerator = mock(SecureStorageKeyGenerator.class);
        UserEntity user = user(8L);
        SecureImageSource source = new SecureImageSource("receipt.png", "image/png", new byte[]{1, 2});
        NormalizedImage normalized = new NormalizedImage(
                new byte[]{3, 4}, "image/png", "png", 2, 2,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );
        SecureAttachmentEntity saved = savedAttachment(user);
        when(userRepository.findById(8L)).thenReturn(Optional.of(user));
        when(inputReader.read(any())).thenReturn(source);
        when(scanner.scan(source.bytes())).thenReturn(MalwareScanResult.CLEAN);
        when(normalizer.normalize(source)).thenReturn(normalized);
        when(normalizer.sanitizeFilename(source.originalFilename())).thenReturn("receipt.png");
        when(keyGenerator.generate("png")).thenReturn("ab/ab123456-1234-1234-1234-123456789012.png");
        when(attachmentRepository.saveAndFlush(any())).thenReturn(saved);
        SecureAttachmentService service = service(
                userRepository, attachmentRepository, inputReader, normalizer, scanner, storage, keyGenerator
        );

        StoredSecureAttachment result = service.storeImage(
                8L,
                SecureAttachmentPurpose.PAYMENT_RECEIPT,
                command()
        );

        assertThat(result.id()).isEqualTo(71L);
        verify(scanner).scan(source.bytes());
        verify(storage).store("ab/ab123456-1234-1234-1234-123456789012.png", normalized.bytes());
        verify(storage, never()).deleteIfExists(any());
    }

    @Test
    void rejectsMalwareBeforeDecodingOrStoring() {
        UserRepository userRepository = mock(UserRepository.class);
        SecureAttachmentRepository attachmentRepository = mock(SecureAttachmentRepository.class);
        SecureUploadInputReader inputReader = mock(SecureUploadInputReader.class);
        SecureImageNormalizer normalizer = mock(SecureImageNormalizer.class);
        MalwareScanner scanner = mock(MalwareScanner.class);
        PrivateAttachmentStorage storage = mock(PrivateAttachmentStorage.class);
        SecureStorageKeyGenerator keyGenerator = mock(SecureStorageKeyGenerator.class);
        SecureImageSource source = new SecureImageSource("receipt.png", "image/png", new byte[]{1});
        when(userRepository.findById(8L)).thenReturn(Optional.of(user(8L)));
        when(inputReader.read(any())).thenReturn(source);
        when(scanner.scan(source.bytes())).thenReturn(MalwareScanResult.INFECTED);
        SecureAttachmentService service = service(
                userRepository, attachmentRepository, inputReader, normalizer, scanner, storage, keyGenerator
        );

        assertThatThrownBy(() -> service.storeImage(
                8L,
                SecureAttachmentPurpose.PAYMENT_RECEIPT,
                command()
        )).isInstanceOfSatisfying(SecureUploadException.class, exception ->
                assertThat(exception.getFailure()).isEqualTo(SecureUploadFailure.MALWARE_DETECTED));
        verifyNoInteractions(normalizer, storage, attachmentRepository, keyGenerator);
    }

    @Test
    void removesTheStoredFileWhenMetadataPersistenceFails() {
        UserRepository userRepository = mock(UserRepository.class);
        SecureAttachmentRepository attachmentRepository = mock(SecureAttachmentRepository.class);
        SecureUploadInputReader inputReader = mock(SecureUploadInputReader.class);
        SecureImageNormalizer normalizer = mock(SecureImageNormalizer.class);
        MalwareScanner scanner = mock(MalwareScanner.class);
        PrivateAttachmentStorage storage = mock(PrivateAttachmentStorage.class);
        SecureStorageKeyGenerator keyGenerator = mock(SecureStorageKeyGenerator.class);
        SecureImageSource source = new SecureImageSource("receipt.png", "image/png", new byte[]{1});
        NormalizedImage normalized = new NormalizedImage(
                new byte[]{2}, "image/png", "png", 1, 1,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );
        String storageKey = "ab/ab123456-1234-1234-1234-123456789012.png";
        when(userRepository.findById(8L)).thenReturn(Optional.of(user(8L)));
        when(inputReader.read(any())).thenReturn(source);
        when(scanner.scan(source.bytes())).thenReturn(MalwareScanResult.CLEAN);
        when(normalizer.normalize(source)).thenReturn(normalized);
        when(normalizer.sanitizeFilename(source.originalFilename())).thenReturn("receipt.png");
        when(keyGenerator.generate("png")).thenReturn(storageKey);
        when(attachmentRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("database failure"));
        SecureAttachmentService service = service(
                userRepository, attachmentRepository, inputReader, normalizer, scanner, storage, keyGenerator
        );

        assertThatThrownBy(() -> service.storeImage(
                8L,
                SecureAttachmentPurpose.PAYMENT_RECEIPT,
                command()
        )).isInstanceOf(IllegalStateException.class);
        verify(storage).deleteIfExists(storageKey);
    }

    private SecureAttachmentService service(
            UserRepository userRepository,
            SecureAttachmentRepository attachmentRepository,
            SecureUploadInputReader inputReader,
            SecureImageNormalizer normalizer,
            MalwareScanner scanner,
            PrivateAttachmentStorage storage,
            SecureStorageKeyGenerator keyGenerator
    ) {
        return new SecureAttachmentService(
                userRepository,
                attachmentRepository,
                inputReader,
                normalizer,
                scanner,
                storage,
                keyGenerator,
                CLOCK
        );
    }

    private SecureImageUploadCommand command() {
        return new SecureImageUploadCommand(
                "receipt.png", "image/png", 1, new ByteArrayInputStream(new byte[]{1})
        );
    }

    private UserEntity user(long id) {
        UserEntity user = new UserEntity();
        user.setId(id);
        return user;
    }

    private SecureAttachmentEntity savedAttachment(UserEntity user) {
        SecureAttachmentEntity attachment = mock(SecureAttachmentEntity.class);
        when(attachment.getId()).thenReturn(71L);
        when(attachment.getOwnerUser()).thenReturn(user);
        when(attachment.getPurpose()).thenReturn(SecureAttachmentPurpose.PAYMENT_RECEIPT);
        when(attachment.getOriginalFilename()).thenReturn("receipt.png");
        when(attachment.getMediaType()).thenReturn("image/png");
        when(attachment.getSizeBytes()).thenReturn(2L);
        when(attachment.getWidthPixels()).thenReturn(2);
        when(attachment.getHeightPixels()).thenReturn(2);
        when(attachment.getSha256()).thenReturn(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );
        when(attachment.getScannedAt()).thenReturn(LocalDateTime.now(CLOCK));
        return attachment;
    }
}
