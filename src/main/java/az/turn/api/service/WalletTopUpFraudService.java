package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WalletTopUpFraudService {
    private final WalletTopUpRequestRepository requestRepository;
    private final AdminAccountRepository adminRepository;
    private final UserRepository userRepository;
    private final WalletAccountRepository walletAccountRepository;
    private final WalletTransactionService walletTransactionService;
    private final WalletTransactionRepository walletTransactionRepository;
    private final FraudulentSubscriptionCancellationService cancellationService;
    private final AdminTopUpRequestMapper mapper;
    private final PlatformAuditService auditService;
    private final Clock clock;

    public WalletTopUpFraudService(
            WalletTopUpRequestRepository requestRepository,
            AdminAccountRepository adminRepository,
            UserRepository userRepository,
            WalletAccountRepository walletAccountRepository,
            WalletTransactionService walletTransactionService,
            WalletTransactionRepository walletTransactionRepository,
            FraudulentSubscriptionCancellationService cancellationService,
            AdminTopUpRequestMapper mapper,
            PlatformAuditService auditService,
            Clock clock
    ) {
        this.requestRepository = requestRepository;
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.walletAccountRepository = walletAccountRepository;
        this.walletTransactionService = walletTransactionService;
        this.walletTransactionRepository = walletTransactionRepository;
        this.cancellationService = cancellationService;
        this.mapper = mapper;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public AdminTopUpRequestDto confirm(
            long requestId,
            String adminUsername,
            AdminTopUpFraudRequestDto suppliedRequest
    ) {
        AdminAccountEntity admin = requireAdmin(adminUsername);
        WalletTopUpRequestEntity request = requireReviewableRequest(requestId);
        UserEntity user = userRepository.findByIdForUpdate(request.getUser().getId())
                .orElseThrow(() -> new IllegalStateException("İstifadəçi tapılmadı."));
        String reason = suppliedRequest.reason().trim();
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> cancelledPayments = List.of();
        WalletTransactionEntity reversal = null;
        if (request.getStatus() == WalletTopUpRequestStatus.AUTO_CREDITED_PENDING_REVIEW) {
            WalletAccountEntity wallet = walletAccountRepository.findByUserIdForUpdate(user.getId())
                    .orElseThrow(() -> new IllegalStateException("İstifadəçinin balans hesabı yaradılmayıb."));
            cancelledPayments = cancellationService.recoverSpentCoins(
                    request,
                    wallet,
                    admin,
                    adminUsername,
                    reason,
                    now
            );
            reversal = reverseTopUp(request, adminUsername);
        }
        int fraudCount = user.registerConfirmedWalletFraud();
        request.confirmFraud(admin, reversal, fraudCount, reason, now);
        userRepository.save(user);
        requestRepository.saveAndFlush(request);
        auditService.record(
                "ADMIN",
                adminUsername,
                "WALLET_TOP_UP_FRAUD_CONFIRMED",
                "WALLET_TOP_UP_REQUEST",
                requestId,
                "coins=" + request.getCoinAmount()
                        + ",fraudCount=" + fraudCount
                        + ",cancelledSubscriptionPayments=" + cancelledPayments
                        + ",reason=" + reason
        );
        return mapper.toDto(request);
    }

    private WalletTransactionEntity reverseTopUp(WalletTopUpRequestEntity request, String adminUsername) {
        WalletTransactionDto reversal = walletTransactionService.apply(
                request.getUser().getId(),
                new WalletTransactionCommandDto(
                        WalletTransactionType.TOP_UP_REVERSAL,
                        request.getCoinAmount(),
                        WalletActorType.ADMIN,
                        null,
                        adminUsername,
                        "top-up-fraud-reversal:" + request.getId(),
                        "Təsdiqlənmiş fırıldaq balans artırması geri çəkildi."
                )
        );
        return walletTransactionRepository.findById(reversal.id())
                .orElseThrow(() -> new IllegalStateException("Coin geri çəkmə əməliyyatı saxlanılmadı."));
    }

    private WalletTopUpRequestEntity requireReviewableRequest(long requestId) {
        WalletTopUpRequestEntity request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Balans artırma sorğusu tapılmadı."));
        if (request.getStatus() != WalletTopUpRequestStatus.AUTO_CREDITED_PENDING_REVIEW
                && request.getStatus() != WalletTopUpRequestStatus.MANUAL_REVIEW
                && request.getStatus() != WalletTopUpRequestStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yalnız yoxlanılan çek fırıldaq kimi təsdiqlənə bilər.");
        }
        return request;
    }

    private AdminAccountEntity requireAdmin(String username) {
        return adminRepository.findByUsernameForUpdate(username)
                .filter(AdminAccountEntity::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin hesabı aktiv deyil."));
    }
}
