package az.turn.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

@Service
public class QrPosterService {
    private final QrCredentialService qrCredentialService;
    private final QrPosterPdfRenderer pdfRenderer;
    private final String publicBaseUrl;

    public QrPosterService(
            QrCredentialService qrCredentialService,
            QrPosterPdfRenderer pdfRenderer,
            @Value("${app.public-base-url:https://novbetime.az}") String publicBaseUrl
    ) {
        this.qrCredentialService = qrCredentialService;
        this.pdfRenderer = pdfRenderer;
        this.publicBaseUrl = publicBaseUrl;
    }

    public QrPosterFile create(long roomId, long credentialId, long userId) {
        QrPosterSpecification data = qrCredentialService.posterData(roomId, credentialId, userId, publicBaseUrl);
        String filename = safeFilename(data.posterTitle()) + "-qr-" + data.credentialId() + ".pdf";
        return new QrPosterFile(pdfRenderer.render(data), filename);
    }

    private String safeFilename(String value) {
        String transliterated = value.toLowerCase(Locale.ROOT)
                .replace("ə", "e")
                .replace("ö", "o")
                .replace("ü", "u")
                .replace("ğ", "g")
                .replace("ı", "i")
                .replace("ş", "s")
                .replace("ç", "c");
        String normalized = Normalizer.normalize(transliterated, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "novbetime" : normalized;
    }
}
