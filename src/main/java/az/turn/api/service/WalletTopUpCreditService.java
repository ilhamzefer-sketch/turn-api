package az.turn.api;

import org.springframework.stereotype.Service;

@Service
public class WalletTopUpCreditService {
    private final WalletTransactionService walletTransactionService;
    private final WalletTransactionRepository walletTransactionRepository;

    public WalletTopUpCreditService(
            WalletTransactionService walletTransactionService,
            WalletTransactionRepository walletTransactionRepository
    ) {
        this.walletTransactionService = walletTransactionService;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    public WalletTransactionEntity credit(WalletTopUpRequestEntity request) {
        long requestId = requireRequestId(request);
        String reference = "top-up-request:" + requestId;
        WalletTransactionDto transaction = walletTransactionService.apply(
                request.getUser().getId(),
                new WalletTransactionCommandDto(
                        WalletTransactionType.TOP_UP,
                        request.getCoinAmount(),
                        WalletActorType.SYSTEM,
                        null,
                        "receipt-auto-credit",
                        reference,
                        "Çek yükləndikdən sonra coin avtomatik əlavə edildi."
                )
        );
        return walletTransactionRepository.findById(transaction.id())
                .orElseThrow(() -> new IllegalStateException("Balans əməliyyatı saxlanılmadı."));
    }

    private long requireRequestId(WalletTopUpRequestEntity request) {
        if (request == null || request.getId() == null) {
            throw new IllegalArgumentException("Balans artırma sorğusu saxlanılmış olmalıdır.");
        }
        return request.getId();
    }
}
