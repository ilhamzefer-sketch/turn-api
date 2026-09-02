package az.turn.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class SecureAttachmentPersistenceIntegrationTests {
    @Autowired
    private SecureAttachmentRepository attachmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void persistsOnlyCleanPrivateImageMetadata() {
        UserEntity user = new UserEntity();
        user.setFirstName("Secure");
        user.setLastName("Attachment");
        user.setNormalizedPhone("+994501293501");
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.saveAndFlush(user);
        LocalDateTime scannedAt = LocalDateTime.of(2026, 8, 31, 12, 0);

        SecureAttachmentEntity attachment = attachmentRepository.saveAndFlush(new SecureAttachmentEntity(
                user,
                SecureAttachmentPurpose.PAYMENT_RECEIPT,
                "ab/ab123456-1234-1234-1234-123456789012.png",
                "ödəniş.png",
                "image/png",
                "png",
                128,
                2,
                2,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                scannedAt
        ));

        assertThat(attachmentRepository.findByIdAndOwnerUserId(attachment.getId(), user.getId()))
                .contains(attachment);
        assertThat(attachment.getScanStatus()).isEqualTo(SecureAttachmentScanStatus.CLEAN);
        assertThat(attachment.getScannedAt()).isEqualTo(scannedAt);
    }

    @Test
    void persistsPdfOnlyAsAPaymentReceiptWithoutImageDimensions() {
        UserEntity user = new UserEntity();
        user.setFirstName("PDF");
        user.setLastName("Receipt");
        user.setNormalizedPhone("+994501293503");
        user.setPasswordHash("test-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.saveAndFlush(user);
        LocalDateTime scannedAt = LocalDateTime.of(2026, 9, 2, 9, 0);

        SecureAttachmentEntity attachment = attachmentRepository.saveAndFlush(new SecureAttachmentEntity(
                user,
                SecureAttachmentPurpose.PAYMENT_RECEIPT,
                "cd/cd123456-1234-1234-1234-123456789012.pdf",
                "ödəniş.pdf",
                "application/pdf",
                "pdf",
                256,
                0,
                0,
                "1123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                scannedAt
        ));

        assertThat(attachmentRepository.findByIdAndOwnerUserId(attachment.getId(), user.getId()))
                .contains(attachment);
        assertThat(attachment.getMediaType()).isEqualTo("application/pdf");
        assertThat(attachment.getWidthPixels()).isZero();
        assertThat(attachment.getHeightPixels()).isZero();
    }
}
