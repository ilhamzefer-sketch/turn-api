package az.turn.api;

public record SecureImageSource(
        String originalFilename,
        String declaredMediaType,
        byte[] bytes
) {
}
