package az.turn.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletTopUpRequestDto(
        long id,
        String packageCode,
        BigDecimal amountAzn,
        long coinAmount,
        String currency,
        String paymentUrl,
        WalletTopUpRequestStatus status,
        LocalDateTime clickedAt,
        LocalDateTime receiptDeadlineAt,
        LocalDateTime receiptUploadedAt,
        boolean receiptUploadOpen,
        String paymentProvider,
        String externalOrderId,
        WalletCheckoutState checkoutState
) {
}
