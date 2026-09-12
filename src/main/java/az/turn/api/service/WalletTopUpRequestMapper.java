package az.turn.api;

import java.time.LocalDateTime;

final class WalletTopUpRequestMapper {
    private WalletTopUpRequestMapper() {}

    static WalletTopUpRequestDto map(WalletTopUpRequestEntity request, LocalDateTime now) {
        return new WalletTopUpRequestDto(
                request.getId(),
                request.getTopUpPackage().getCode(),
                request.getAmountAzn(),
                request.getCoinAmount(),
                request.getCurrency(),
                "manual".equals(request.getPaymentProvider()) || (request.getStatus() == WalletTopUpRequestStatus.AWAITING_RECEIPT
                        && request.getCheckoutState() == WalletCheckoutState.READY)
                        ? request.getPaymentUrl() : null,
                request.getStatus(),
                request.getClickedAt(),
                request.getReceiptDeadlineAt(),
                request.getReceiptUploadedAt(),
                request.isReceiptWindowOpen(now),
                request.getPaymentProvider(),
                request.getExternalOrderId(),
                request.getCheckoutState()
        );
    }

}
