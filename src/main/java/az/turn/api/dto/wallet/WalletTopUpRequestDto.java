package az.turn.api;

import java.time.LocalDateTime;

public record WalletTopUpRequestDto(
        long id,
        String packageCode,
        int amountAzn,
        long coinAmount,
        String currency,
        String paymentUrl,
        WalletTopUpRequestStatus status,
        LocalDateTime clickedAt,
        LocalDateTime receiptDeadlineAt,
        LocalDateTime receiptUploadedAt,
        boolean receiptUploadOpen
) {
}
