package az.turn.api;

public record NormalizedAttachment(
        String originalFilename,
        byte[] bytes,
        String mediaType,
        String fileExtension,
        int widthPixels,
        int heightPixels,
        String sha256
) {
}
