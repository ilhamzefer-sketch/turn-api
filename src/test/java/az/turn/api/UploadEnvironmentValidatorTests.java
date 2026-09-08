package az.turn.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadEnvironmentValidatorTests {
    @Test
    void requiresAntivirusInStageAndProduction() {
        UploadProperties properties = SecureUploadTestProperties.properties(absolutePrivateUploadsPath());

        assertThrows(
                IllegalStateException.class,
                () -> new UploadEnvironmentValidator("stage", properties).validate()
        );
        assertThrows(
                IllegalStateException.class,
                () -> new UploadEnvironmentValidator("prod", properties).validate()
        );
    }

    @Test
    void acceptsAnAbsolutePrivatePathAndEnabledAntivirus() {
        UploadProperties base = SecureUploadTestProperties.properties(absolutePrivateUploadsPath());
        UploadProperties properties = new UploadProperties(
                base.storageRoot(),
                base.maxFileBytes(),
                base.maxDimensionPixels(),
                base.maxImagePixels(),
                SecureUploadTestProperties.antivirus(3310)
        );

        assertDoesNotThrow(() -> new UploadEnvironmentValidator("stage", properties).validate());
        assertDoesNotThrow(() -> new UploadEnvironmentValidator("prod", properties).validate());
    }

    @Test
    void allowsDisabledAntivirusOnlyForLocalAndTestEnvironments() {
        UploadProperties properties = SecureUploadTestProperties.properties(Path.of("relative/uploads"));

        assertDoesNotThrow(() -> new UploadEnvironmentValidator("local", properties).validate());
        assertDoesNotThrow(() -> new UploadEnvironmentValidator("test", properties).validate());
    }

    private Path absolutePrivateUploadsPath() {
        return Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("private/uploads")
                .toAbsolutePath();
    }
}
