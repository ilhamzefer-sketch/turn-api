package az.turn.api;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Component
public class SecureUploadInputReader {
    private final UploadProperties properties;

    public SecureUploadInputReader(UploadProperties properties) {
        this.properties = properties;
    }

    public SecureImageSource read(SecureImageUploadCommand command) {
        Objects.requireNonNull(command);
        if (command.declaredSizeBytes() == 0) {
            throw failure(SecureUploadFailure.EMPTY_FILE, "Boş fayl yükləmək olmaz.");
        }
        if (command.declaredSizeBytes() < 0 || command.declaredSizeBytes() > properties.maxFileBytes()) {
            throw failure(SecureUploadFailure.FILE_TOO_LARGE, "Faylın ölçüsü 5 MB-dan böyük ola bilməz.");
        }
        if (command.inputStream() == null) {
            throw failure(SecureUploadFailure.EMPTY_FILE, "Fayl məlumatı yoxdur.");
        }
        byte[] bytes = readBounded(command.inputStream());
        if (bytes.length == 0) {
            throw failure(SecureUploadFailure.EMPTY_FILE, "Boş fayl yükləmək olmaz.");
        }
        return new SecureImageSource(command.originalFilename(), command.declaredMediaType(), bytes);
    }

    private byte[] readBounded(InputStream inputStream) {
        try (InputStream source = inputStream) {
            byte[] bytes = source.readNBytes(properties.maxFileBytes() + 1);
            if (bytes.length > properties.maxFileBytes()) {
                throw failure(SecureUploadFailure.FILE_TOO_LARGE, "Faylın ölçüsü 5 MB-dan böyük ola bilməz.");
            }
            return bytes;
        } catch (IOException exception) {
            throw new SecureUploadException(
                    SecureUploadFailure.INVALID_IMAGE,
                    "Fayl oxuna bilmədi.",
                    exception
            );
        }
    }

    private SecureUploadException failure(SecureUploadFailure failure, String message) {
        return new SecureUploadException(failure, message);
    }
}
