package az.turn.api;

import java.time.LocalDateTime;

public record AdminTopUpRequestDto(
        long id,
        long userId,
        String firstName,
        String lastName,
        String phone,
        String packageCode,
        int amountAzn,
        long coinAmount,
        String currency,
        String status,
        LocalDateTime clickedAt,
        LocalDateTime receiptDeadlineAt,
        LocalDateTime receiptUploadedAt,
        Long receiptAttachmentId,
        String receiptMediaType,
        Long receiptSizeBytes,
        int confirmedFraudCount,
        Integer fraudCountAfter,
        LocalDateTime reviewedAt,
        String resolutionNote
) {
}
