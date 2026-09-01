package az.turn.api;

public record NormalizedImage(
        byte[] bytes,
        String mediaType,
        String fileExtension,
        int widthPixels,
        int heightPixels,
        String sha256
) {
}
