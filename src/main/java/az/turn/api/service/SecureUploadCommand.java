package az.turn.api;

import java.io.InputStream;

public record SecureUploadCommand(
        String originalFilename,
        String declaredMediaType,
        long declaredSizeBytes,
        InputStream inputStream
) {
}
