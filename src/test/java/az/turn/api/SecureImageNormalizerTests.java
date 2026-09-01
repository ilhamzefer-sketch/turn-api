package az.turn.api;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureImageNormalizerTests {
    private final SecureImageNormalizer normalizer = new SecureImageNormalizer(
            SecureUploadTestProperties.properties(Path.of("/tmp/test"), 1_000_000, 10, 100)
    );

    @Test
    void validatesAndReencodesPngWithoutTrailingPayload() throws Exception {
        byte[] image = image("png", 2, 2);
        byte[] payload = "<script>payload</script>".getBytes(StandardCharsets.UTF_8);
        byte[] polyglot = new byte[image.length + payload.length];
        System.arraycopy(image, 0, polyglot, 0, image.length);
        System.arraycopy(payload, 0, polyglot, image.length, payload.length);

        NormalizedImage normalized = normalizer.normalize(
                new SecureImageSource("ödəniş çeki.png", "image/png", polyglot)
        );

        assertThat(normalized.mediaType()).isEqualTo("image/png");
        assertThat(normalized.fileExtension()).isEqualTo("png");
        assertThat(normalized.widthPixels()).isEqualTo(2);
        assertThat(normalized.heightPixels()).isEqualTo(2);
        assertThat(normalized.sha256()).hasSize(64);
        assertThat(new String(normalized.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("payload");
        assertThat(normalizer.sanitizeFilename("ödəniş çeki.png")).isEqualTo("ödəniş çeki.png");
    }

    @Test
    void acceptsJpegAndNormalizesItsExtension() throws Exception {
        NormalizedImage normalized = normalizer.normalize(
                new SecureImageSource("receipt.jpeg", "image/jpeg", image("jpg", 2, 2))
        );

        assertThat(normalized.mediaType()).isEqualTo("image/jpeg");
        assertThat(normalized.fileExtension()).isEqualTo("jpg");
    }

    @Test
    void rejectsMismatchedMimeExtensionAndMagicBytes() throws Exception {
        assertFailure(
                new SecureImageSource("receipt.png", "image/jpeg", image("png", 2, 2)),
                SecureUploadFailure.UNSUPPORTED_FILE_TYPE
        );
        assertFailure(
                new SecureImageSource("receipt.jpg", "image/png", image("png", 2, 2)),
                SecureUploadFailure.UNSUPPORTED_FILE_TYPE
        );
        assertFailure(
                new SecureImageSource("receipt.png", "image/png", "not-an-image".getBytes(StandardCharsets.UTF_8)),
                SecureUploadFailure.UNSUPPORTED_FILE_TYPE
        );
    }

    @Test
    void rejectsImagesOutsidePixelAndDimensionLimits() throws Exception {
        assertFailure(
                new SecureImageSource("large.png", "image/png", image("png", 11, 1)),
                SecureUploadFailure.IMAGE_DIMENSIONS_EXCEEDED
        );
    }

    @Test
    void rejectsPathLikeAndBlankFilenames() {
        assertThatThrownBy(() -> normalizer.sanitizeFilename("../receipt.png"))
                .isInstanceOf(SecureUploadException.class);
        assertThatThrownBy(() -> normalizer.sanitizeFilename("  "))
                .isInstanceOf(SecureUploadException.class);
    }

    private byte[] image(String format, int width, int height) throws Exception {
        int type = format.equals("jpg") ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage image = new BufferedImage(width, height, type);
        image.setRGB(0, 0, Color.BLUE.getRGB());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(image, format, output)).isTrue();
            return output.toByteArray();
        }
    }

    private void assertFailure(SecureImageSource source, SecureUploadFailure expected) {
        assertThatThrownBy(() -> normalizer.normalize(source))
                .isInstanceOfSatisfying(SecureUploadException.class, exception ->
                        assertThat(exception.getFailure()).isEqualTo(expected));
    }
}
