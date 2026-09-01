package az.turn.api;

import java.nio.file.Path;
import java.time.Duration;

public final class SecureUploadTestProperties {
    private SecureUploadTestProperties() {
    }

    public static UploadProperties properties(Path storageRoot) {
        return properties(storageRoot, 5_242_880, 10_000, 25_000_000);
    }

    public static UploadProperties properties(
            Path storageRoot,
            int maxFileBytes,
            int maxDimensionPixels,
            long maxImagePixels
    ) {
        return new UploadProperties(
                storageRoot,
                maxFileBytes,
                maxDimensionPixels,
                maxImagePixels,
                new AntivirusProperties(
                        false,
                        "127.0.0.1",
                        3310,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        8192
                )
        );
    }

    public static AntivirusProperties antivirus(int port) {
        return new AntivirusProperties(
                true,
                "127.0.0.1",
                port,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1024
        );
    }
}
