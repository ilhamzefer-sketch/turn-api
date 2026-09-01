package az.turn.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemPrivateAttachmentStorageTests {
    private static final String STORAGE_KEY = "ab/ab123456-1234-1234-1234-123456789012.png";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void storesAndReadsPrivateContentUsingASafeKey() {
        FileSystemPrivateAttachmentStorage storage = storage(10);
        byte[] content = {1, 2, 3};

        storage.store(STORAGE_KEY, content);

        assertThat(storage.read(STORAGE_KEY)).containsExactly(content);
        assertThat(Files.exists(temporaryDirectory.resolve(STORAGE_KEY))).isTrue();
        storage.deleteIfExists(STORAGE_KEY);
        assertThat(Files.exists(temporaryDirectory.resolve(STORAGE_KEY))).isFalse();
    }

    @Test
    void rejectsTraversalInvalidKeysAndOversizedContent() {
        FileSystemPrivateAttachmentStorage storage = storage(2);

        assertThatThrownBy(() -> storage.store("../outside.png", new byte[]{1}))
                .isInstanceOf(SecureUploadException.class);
        assertThatThrownBy(() -> storage.store(STORAGE_KEY, new byte[]{1, 2, 3}))
                .isInstanceOfSatisfying(SecureUploadException.class, exception ->
                        assertThat(exception.getFailure()).isEqualTo(SecureUploadFailure.STORAGE_UNAVAILABLE));
    }

    private FileSystemPrivateAttachmentStorage storage(int maxFileBytes) {
        FileSystemPrivateAttachmentStorage storage = new FileSystemPrivateAttachmentStorage(
                SecureUploadTestProperties.properties(temporaryDirectory, maxFileBytes, 10, 100)
        );
        storage.initialize();
        return storage;
    }
}
