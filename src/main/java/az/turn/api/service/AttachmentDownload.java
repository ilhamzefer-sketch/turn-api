package az.turn.api;

public record AttachmentDownload(
        byte[] bytes,
        String mediaType,
        String filename
) {
}
