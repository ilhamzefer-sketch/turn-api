package az.turn.api;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurePdfValidatorTests {
    private final SecurePdfValidator validator = new SecurePdfValidator();

    @Test
    void acceptsACompleteNonInteractivePdf() throws Exception {
        byte[] bytes = pdf(false, false);

        NormalizedAttachment attachment = validator.validate(
                new SecureUploadSource("kapital-çeki.pdf", "application/pdf", bytes),
                "kapital-çeki.pdf"
        );

        assertThat(attachment.mediaType()).isEqualTo("application/pdf");
        assertThat(attachment.fileExtension()).isEqualTo("pdf");
        assertThat(attachment.widthPixels()).isZero();
        assertThat(attachment.heightPixels()).isZero();
        assertThat(attachment.sha256()).hasSize(64);
        assertThat(attachment.bytes()).containsExactly(bytes);
    }

    @Test
    void rejectsMismatchedMetadataAndTrailingPayload() throws Exception {
        byte[] bytes = pdf(false, false);
        assertFailure(
                new SecureUploadSource("receipt.pdf", "image/png", bytes),
                "receipt.pdf",
                SecureUploadFailure.UNSUPPORTED_FILE_TYPE
        );
        assertFailure(
                new SecureUploadSource("receipt.png", "application/pdf", bytes),
                "receipt.png",
                SecureUploadFailure.UNSUPPORTED_FILE_TYPE
        );

        byte[] payload = "<script>payload</script>".getBytes(StandardCharsets.UTF_8);
        byte[] polyglot = new byte[bytes.length + payload.length];
        System.arraycopy(bytes, 0, polyglot, 0, bytes.length);
        System.arraycopy(payload, 0, polyglot, bytes.length, payload.length);
        assertFailure(
                new SecureUploadSource("receipt.pdf", "application/pdf", polyglot),
                "receipt.pdf",
                SecureUploadFailure.INVALID_FILE
        );
    }

    @Test
    void rejectsEncryptedAndInteractivePdfFiles() throws Exception {
        assertFailure(
                new SecureUploadSource("protected.pdf", "application/pdf", pdf(true, false)),
                "protected.pdf",
                SecureUploadFailure.INVALID_FILE
        );
        assertFailure(
                new SecureUploadSource("interactive.pdf", "application/pdf", pdf(false, true)),
                "interactive.pdf",
                SecureUploadFailure.INVALID_FILE
        );
    }

    private byte[] pdf(boolean encrypted, boolean interactive) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            if (interactive) {
                document.getDocumentCatalog().getCOSObject().setItem(COSName.OPEN_ACTION, new COSDictionary());
            }
            if (encrypted) {
                document.protect(new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission()));
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private void assertFailure(
            SecureUploadSource source,
            String filename,
            SecureUploadFailure expected
    ) {
        assertThatThrownBy(() -> validator.validate(source, filename))
                .isInstanceOfSatisfying(SecureUploadException.class, exception ->
                        assertThat(exception.getFailure()).isEqualTo(expected));
    }
}
