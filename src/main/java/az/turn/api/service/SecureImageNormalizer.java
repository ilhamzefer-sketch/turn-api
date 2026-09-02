package az.turn.api;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;

@Component
public class SecureImageNormalizer {
    private final UploadProperties properties;

    public SecureImageNormalizer(UploadProperties properties) {
        this.properties = properties;
    }

    public NormalizedImage normalize(SecureUploadSource source) {
        SecureImageType type = SecureImageType.detect(source.bytes());
        String filename = sanitizeFilename(source.originalFilename());
        validateDeclaredType(source.declaredMediaType(), type);
        validateExtension(filename, type);
        return decodeAndNormalize(source.bytes(), type);
    }

    public String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) {
            throw failure(SecureUploadFailure.INVALID_IMAGE, "Fayl adı boş ola bilməz.");
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC).trim();
        if (normalized.contains("/") || normalized.contains("\\")) {
            throw failure(SecureUploadFailure.INVALID_IMAGE, "Fayl adı düzgün deyil.");
        }
        String sanitized = normalized.replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            throw failure(SecureUploadFailure.INVALID_IMAGE, "Fayl adı düzgün deyil.");
        }
        return sanitized.length() <= 180 ? sanitized : sanitized.substring(sanitized.length() - 180);
    }

    private NormalizedImage decodeAndNormalize(byte[] bytes, SecureImageType type) {
        try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = reader(input, type);
            try {
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw failure(SecureUploadFailure.INVALID_IMAGE, "Şəkil faylı oxuna bilmədi.");
                }
                byte[] normalized = encode(decoded, type);
                if (normalized.length > properties.maxFileBytes()) {
                    throw failure(SecureUploadFailure.FILE_TOO_LARGE, "Təhlükəsiz şəkil 5 MB limitini keçir.");
                }
                return new NormalizedImage(
                        normalized,
                        type.mediaType(),
                        type.fileExtension(),
                        width,
                        height,
                        sha256(normalized)
                );
            } finally {
                reader.dispose();
            }
        } catch (SecureUploadException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new SecureUploadException(
                    SecureUploadFailure.INVALID_IMAGE,
                    "Şəkil faylı zədəlidir və ya dəstəklənmir.",
                    exception
            );
        }
    }

    private ImageReader reader(MemoryCacheImageInputStream input, SecureImageType type) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            throw failure(SecureUploadFailure.INVALID_IMAGE, "Şəkil faylı oxuna bilmədi.");
        }
        ImageReader reader = readers.next();
        reader.setInput(input, true, true);
        if (!type.matchesReaderFormat(reader.getFormatName())) {
            reader.dispose();
            throw failure(SecureUploadFailure.UNSUPPORTED_FILE_TYPE, "Fayl formatı məlumatları uyğun gəlmir.");
        }
        return reader;
    }

    private byte[] encode(BufferedImage decoded, SecureImageType type) throws IOException {
        int bufferedType = type == SecureImageType.JPEG ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage clean = new BufferedImage(decoded.getWidth(), decoded.getHeight(), bufferedType);
        Graphics2D graphics = clean.createGraphics();
        if (type == SecureImageType.JPEG) {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, clean.getWidth(), clean.getHeight());
        }
        graphics.drawImage(decoded, 0, 0, null);
        graphics.dispose();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(clean, type.fileExtension(), output)) {
                throw failure(SecureUploadFailure.INVALID_IMAGE, "Şəkil təhlükəsiz formata çevrilə bilmədi.");
            }
            return output.toByteArray();
        }
    }

    private void validateDimensions(int width, int height) {
        long pixels = Math.multiplyExact((long) width, height);
        if (width <= 0 || height <= 0
                || width > properties.maxDimensionPixels()
                || height > properties.maxDimensionPixels()
                || pixels > properties.maxImagePixels()) {
            throw failure(
                    SecureUploadFailure.IMAGE_DIMENSIONS_EXCEEDED,
                    "Şəklin ölçüləri təhlükəsizlik limitini keçir."
            );
        }
    }

    private void validateDeclaredType(String declaredMediaType, SecureImageType type) {
        if (declaredMediaType == null) {
            throw failure(SecureUploadFailure.UNSUPPORTED_FILE_TYPE, "Faylın media tipi yoxdur.");
        }
        String normalized = declaredMediaType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(type.mediaType())) {
            throw failure(SecureUploadFailure.UNSUPPORTED_FILE_TYPE, "Faylın media tipi real formatla uyğun deyil.");
        }
    }

    private void validateExtension(String filename, SecureImageType type) {
        int separator = filename.lastIndexOf('.');
        String extension = separator < 0 ? "" : filename.substring(separator + 1);
        if (!type.acceptsExtension(extension)) {
            throw failure(SecureUploadFailure.UNSUPPORTED_FILE_TYPE, "Fayl uzantısı real formatla uyğun deyil.");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 dəstəklənmir.", exception);
        }
    }

    private SecureUploadException failure(SecureUploadFailure failure, String message) {
        return new SecureUploadException(failure, message);
    }
}
