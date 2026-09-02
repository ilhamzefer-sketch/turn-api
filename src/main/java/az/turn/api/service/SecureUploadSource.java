package az.turn.api;

public record SecureUploadSource(
        String originalFilename,
        String declaredMediaType,
        byte[] bytes
) {
}
