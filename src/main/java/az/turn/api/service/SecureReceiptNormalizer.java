package az.turn.api;

import org.springframework.stereotype.Component;

@Component
public class SecureReceiptNormalizer {
    private final SecureImageNormalizer imageNormalizer;
    private final SecurePdfValidator pdfValidator;

    public SecureReceiptNormalizer(
            SecureImageNormalizer imageNormalizer,
            SecurePdfValidator pdfValidator
    ) {
        this.imageNormalizer = imageNormalizer;
        this.pdfValidator = pdfValidator;
    }

    public NormalizedAttachment normalizeReceipt(SecureUploadSource source) {
        String filename = imageNormalizer.sanitizeFilename(source.originalFilename());
        if (pdfValidator.hasPdfSignature(source.bytes())) {
            return pdfValidator.validate(source, filename);
        }
        return normalizeImage(source, filename);
    }

    public NormalizedAttachment normalizeImage(SecureUploadSource source) {
        return normalizeImage(source, imageNormalizer.sanitizeFilename(source.originalFilename()));
    }

    private NormalizedAttachment normalizeImage(SecureUploadSource source, String filename) {
        NormalizedImage image = imageNormalizer.normalize(source);
        return new NormalizedAttachment(
                filename,
                image.bytes(),
                image.mediaType(),
                image.fileExtension(),
                image.widthPixels(),
                image.heightPixels(),
                image.sha256()
        );
    }
}
