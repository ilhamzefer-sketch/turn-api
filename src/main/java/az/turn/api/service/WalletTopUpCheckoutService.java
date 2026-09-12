package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class WalletTopUpCheckoutService {
    private final UserRepository userRepository;
    private final WalletTopUpPackageRepository packageRepository;
    private final WalletTopUpRequestRepository requestRepository;
    private final Clock clock;

    public WalletTopUpCheckoutService(UserRepository userRepository,
            WalletTopUpPackageRepository packageRepository, WalletTopUpRequestRepository requestRepository, Clock clock) {
        this.userRepository = userRepository;
        this.packageRepository = packageRepository;
        this.requestRepository = requestRepository;
        this.clock = clock;
    }

    @Transactional
    public WalletTopUpPreparationDto prepare(long userId, String packageCode, boolean external) {
        UserEntity user = userRepository.findById(userId).orElseThrow(this::notFound);
        String code = packageCode == null ? "" : packageCode.trim().toUpperCase(Locale.ROOT);
        WalletTopUpPackageEntity topUpPackage = packageRepository.findById(code)
                .filter(WalletTopUpPackageEntity::isActive)
                .orElseThrow(() -> new WalletTopUpException(WalletTopUpFailure.PACKAGE_NOT_FOUND,
                        "Seçilmiş balans paketi mövcud deyil."));
        LocalDateTime now = LocalDateTime.now(clock);
        WalletTopUpRequestEntity active = requestRepository.findActiveByUserIdForUpdate(userId).orElse(null);
        if (active != null) {
            boolean awaiting = active.getStatus() == WalletTopUpRequestStatus.AWAITING_RECEIPT;
            boolean epoint = "epoint".equals(active.getPaymentProvider());
            if (awaiting && epoint && active.getCheckoutState() != WalletCheckoutState.READY) {
                if (!code.equals(active.getTopUpPackage().getCode())) {
                    throw new WalletTopUpException(WalletTopUpFailure.ACTIVE_REQUEST_EXISTS,
                            "Əvvəlki ödənişin nəticəsi hələ təsdiqlənməyib.");
                }
                return new WalletTopUpPreparationDto(WalletTopUpRequestMapper.map(active, now), false);
            }
            if (awaiting && active.expire(now)) {
                requestRepository.saveAndFlush(active);
            } else if (awaiting && epoint && external) {
                if (code.equals(active.getTopUpPackage().getCode())) {
                    return new WalletTopUpPreparationDto(WalletTopUpRequestMapper.map(active, now), false);
                }
                active.supersede(now);
                requestRepository.saveAndFlush(active);
            } else {
                throw new WalletTopUpException(WalletTopUpFailure.ACTIVE_REQUEST_EXISTS,
                        "Əvvəlki balans artırma sorğusu tamamlanmalıdır.");
            }
        }
        WalletTopUpRequestEntity request = requestRepository.saveAndFlush(new WalletTopUpRequestEntity(user, topUpPackage, now));
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

    private WalletTopUpException notFound() {
        return new WalletTopUpException(WalletTopUpFailure.REQUEST_NOT_FOUND, "Balans artırma sorğusu tapılmadı.");
    }
}
