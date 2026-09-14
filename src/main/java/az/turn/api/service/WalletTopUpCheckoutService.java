package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class WalletTopUpCheckoutService {
    private final UserRepository userRepository;
    private final WalletTopUpPackageRepository packageRepository;
    private final WalletTopUpRequestRepository requestRepository;
    private final Clock clock;
    private final WalletTopUpAmountService amounts;

    public WalletTopUpCheckoutService(UserRepository userRepository,
            WalletTopUpPackageRepository packageRepository, WalletTopUpRequestRepository requestRepository, Clock clock, WalletTopUpAmountService amounts) {
        this.userRepository = userRepository;
        this.packageRepository = packageRepository;
        this.requestRepository = requestRepository;
        this.clock = clock;
        this.amounts = amounts;
    }

    @Transactional
    public WalletTopUpPreparationDto prepare(long userId, String packageCode, boolean external) {
        return prepare(userId, new WalletTopUpCreateRequestDto(packageCode, null), external);
    }

    @Transactional
    public WalletTopUpPreparationDto prepare(long userId, WalletTopUpCreateRequestDto selection, boolean external) {
        if (selection == null || !selection.isSelectionValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ödəniş məbləği düzgün deyil.");
        }
        boolean custom = selection.amountAzn() != null;
        if (custom && !external) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kartla ödəniş hazırda əlçatan deyil.");
        }
        WalletTopUpPackageEntity topUpPackage = custom ? null : findPackage(selection.packageCode());
        BigDecimal amount = custom ? amounts.normalize(selection.amountAzn()) : topUpPackage.getAmountAzn();
        long coins = custom ? amounts.coins(amount) : topUpPackage.getCoinAmount();
        UserEntity user = userRepository.findById(userId).orElseThrow(this::notFound);
        LocalDateTime now = LocalDateTime.now(clock);
        WalletTopUpRequestEntity active = requestRepository.findActiveByUserIdForUpdate(userId).orElse(null);
        if (active != null) {
            boolean sameAmount = active.getAmountAzn().compareTo(amount) == 0
                    && active.getCoinAmount() == coins && "AZN".equals(active.getCurrency());
            boolean awaiting = active.getStatus() == WalletTopUpRequestStatus.AWAITING_RECEIPT;
            boolean epoint = "epoint".equals(active.getPaymentProvider());
            if (awaiting && epoint && active.getCheckoutState() != WalletCheckoutState.READY) {
                if (!sameAmount) {
                    throw new WalletTopUpException(WalletTopUpFailure.ACTIVE_REQUEST_EXISTS,
                            "Əvvəlki ödənişin nəticəsi hələ təsdiqlənməyib.");
                }
                return new WalletTopUpPreparationDto(WalletTopUpRequestMapper.map(active, now), false);
            }
            if (awaiting && active.expire(now)) {
                requestRepository.saveAndFlush(active);
            } else if (awaiting && epoint && external) {
                if (sameAmount) {
                    return new WalletTopUpPreparationDto(WalletTopUpRequestMapper.map(active, now), false);
                }
                active.supersede(now);
                requestRepository.saveAndFlush(active);
            } else {
                throw new WalletTopUpException(WalletTopUpFailure.ACTIVE_REQUEST_EXISTS,
                        "Əvvəlki balans artırma sorğusu tamamlanmalıdır.");
            }
        }
        WalletTopUpRequestEntity request = requestRepository.saveAndFlush(custom
                ? new WalletTopUpRequestEntity(user, amount, coins, now)
                : new WalletTopUpRequestEntity(user, topUpPackage, now));
        if (external) {
            request.startExternalPayment("epoint", EpointWalletPaymentService.orderIdFor(request.getId(), clock),
                    "about:blank", now);
            requestRepository.saveAndFlush(request);
        }
        return new WalletTopUpPreparationDto(WalletTopUpRequestMapper.map(request, now), external);
    }

    @Transactional
    public WalletTopUpRequestDto finish(long requestId, String redirectUrl, String transactionId) {
        WalletTopUpRequestEntity request = requestRepository.findByIdForUpdate(requestId).orElseThrow(this::notFound);
        request.recordCheckout(redirectUrl, transactionId, LocalDateTime.now(clock));
        requestRepository.saveAndFlush(request);
        return WalletTopUpRequestMapper.map(request, LocalDateTime.now(clock));
    }

    @Transactional
    public WalletTopUpRequestDto unknown(long requestId) {
        WalletTopUpRequestEntity request = requestRepository.findByIdForUpdate(requestId).orElseThrow(this::notFound);
        request.markCheckoutUnknown(LocalDateTime.now(clock));
        requestRepository.saveAndFlush(request);
        return WalletTopUpRequestMapper.map(request, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public WalletTopUpRequestDto get(long userId, long requestId) {
        WalletTopUpRequestEntity request = requestRepository.findById(requestId)
                .filter(item -> item.getUser().getId().equals(userId)).orElseThrow(this::notFound);
        return WalletTopUpRequestMapper.map(request, LocalDateTime.now(clock));
    }

    private WalletTopUpPackageEntity findPackage(String code) {
        return packageRepository.findById(code.trim().toUpperCase(Locale.ROOT))
                .filter(WalletTopUpPackageEntity::isActive)
                .orElseThrow(() -> new WalletTopUpException(WalletTopUpFailure.PACKAGE_NOT_FOUND,
                        "Seçilmiş balans paketi mövcud deyil."));
    }

    private WalletTopUpException notFound() {
        return new WalletTopUpException(WalletTopUpFailure.REQUEST_NOT_FOUND, "Balans artırma sorğusu tapılmadı.");
    }
}
