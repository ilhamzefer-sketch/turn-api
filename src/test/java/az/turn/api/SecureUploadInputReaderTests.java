package az.turn.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureUploadInputReaderTests {
    private final SecureUploadInputReader reader = new SecureUploadInputReader(
            SecureUploadTestProperties.properties(Path.of("/tmp/test"), 8, 10, 100)
    );

    @Test
    void readsAFileOnlyWithinTheConfiguredLimit() {
        byte[] content = {1, 2, 3};

        SecureImageSource source = reader.read(new SecureImageUploadCommand(
                "receipt.png",
                "image/png",
                content.length,
                new ByteArrayInputStream(content)
        ));

        assertThat(source.bytes()).containsExactly(content);
    }

    @Test
    void rejectsEmptyDeclaredAndActualContent() {
        assertFailure(
                new SecureImageUploadCommand("receipt.png", "image/png", 0, new ByteArrayInputStream(new byte[0])),
                SecureUploadFailure.EMPTY_FILE
        );
        assertFailure(
                new SecureImageUploadCommand("receipt.png", "image/png", 1, new ByteArrayInputStream(new byte[0])),
                SecureUploadFailure.EMPTY_FILE
        );
    }

    @Test
    void rejectsDeclaredOrStreamedContentBeyondTheLimit() {
        assertFailure(
                new SecureImageUploadCommand("receipt.png", "image/png", 9, new ByteArrayInputStream(new byte[1])),
                SecureUploadFailure.FILE_TOO_LARGE
        );
        assertFailure(
                new SecureImageUploadCommand("receipt.png", "image/png", 1, new ByteArrayInputStream(new byte[9])),
                SecureUploadFailure.FILE_TOO_LARGE
        );
    }

    private void assertFailure(SecureImageUploadCommand command, SecureUploadFailure expected) {
        assertThatThrownBy(() -> reader.read(command))
                .isInstanceOfSatisfying(SecureUploadException.class, exception ->
                        assertThat(exception.getFailure()).isEqualTo(expected));
    }
}
