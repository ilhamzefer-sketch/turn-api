package az.turn.api;

import java.util.Locale;
import java.util.Set;

public enum SecureImageType {
    JPEG("image/jpeg", "jpg", Set.of("jpg", "jpeg")),
    PNG("image/png", "png", Set.of("png"));

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final String mediaType;
    private final String fileExtension;
    private final Set<String> acceptedExtensions;

    SecureImageType(String mediaType, String fileExtension, Set<String> acceptedExtensions) {
        this.mediaType = mediaType;
        this.fileExtension = fileExtension;
        this.acceptedExtensions = acceptedExtensions;
    }

    public String mediaType() { return mediaType; }
    public String fileExtension() { return fileExtension; }

    public boolean acceptsExtension(String extension) {
        return acceptedExtensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    public boolean matchesReaderFormat(String formatName) {
        String normalized = formatName.toLowerCase(Locale.ROOT);
        return this == JPEG ? normalized.equals("jpeg") || normalized.equals("jpg") : normalized.equals("png");
    }

    public static SecureImageType detect(byte[] bytes) {
        if (isJpeg(bytes)) {
            return JPEG;
        }
        if (isPng(bytes)) {
            return PNG;
        }
        throw new SecureUploadException(
                SecureUploadFailure.UNSUPPORTED_FILE_TYPE,
                "Yalnız JPEG və PNG şəkilləri qəbul edilir."
        );
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && bytes[0] == (byte) 0xFF
                && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF;
    }

    private static boolean isPng(byte[] bytes) {
        if (bytes.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (bytes[index] != PNG_SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }
}
