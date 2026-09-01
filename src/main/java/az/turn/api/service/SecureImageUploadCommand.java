package az.turn.api;

import java.io.InputStream;

public record SecureImageUploadCommand(
        String originalFilename,
        String declaredMediaType,
        long declaredSizeBytes,
        InputStream inputStream
) {
}
