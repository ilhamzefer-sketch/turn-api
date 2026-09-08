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
        return credit(
                request,
                "receipt-auto-credit",
                "Çek yükləndikdən sonra coin avtomatik əlavə edildi."
        );
    }

    public WalletTransactionEntity creditExternalPayment(WalletTopUpRequestEntity request, String providerName) {
        return credit(
                request,
                providerName,
                "Epoint ödənişi uğurlu olduqdan sonra coin avtomatik əlavə edildi."
        );
    }

    private WalletTransactionEntity credit(
            WalletTopUpRequestEntity request,
            String actorReference,
            String description
    ) {
        long requestId = requireRequestId(request);
        String reference = "top-up-request:" + requestId;
        WalletTransactionDto transaction = walletTransactionService.apply(
                request.getUser().getId(),
                new WalletTransactionCommandDto(
                        WalletTransactionType.TOP_UP,
                        request.getCoinAmount(),
                        WalletActorType.SYSTEM,
                        null,
                        actorReference,
                        reference,
                        description
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
