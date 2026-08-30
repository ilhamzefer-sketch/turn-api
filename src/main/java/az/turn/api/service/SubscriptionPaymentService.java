package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Stream;

@Service
public class SubscriptionPaymentService {
    private final SubscriptionPlanRepository planRepository;
    private final ProviderSubscriptionRepository subscriptionRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final SubscriptionCoinPaymentRepository coinPaymentRepository;
    private final ProviderScopeAccessService scopeAccessService;
    private final SubscriptionMapper mapper;

    public SubscriptionPaymentService(
            SubscriptionPlanRepository planRepository,
            ProviderSubscriptionRepository subscriptionRepository,
            PaymentSessionRepository paymentSessionRepository,
            SubscriptionCoinPaymentRepository coinPaymentRepository,
            ProviderScopeAccessService scopeAccessService,
            SubscriptionMapper mapper
    ) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentSessionRepository = paymentSessionRepository;
        this.coinPaymentRepository = coinPaymentRepository;
        this.scopeAccessService = scopeAccessService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> plans(ProviderScopeType scopeType) {
        List<SubscriptionPlanEntity> plans = scopeType == null
                ? planRepository.findByActiveTrueOrderByCoinPriceAsc()
                : planRepository.findByActiveTrueAndScopeTypeOrderByCoinPriceAsc(scopeType);
        return plans.stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ProviderSubscriptionDto get(ProviderScopeType scopeType, long scopeId, long userId) {
        scopeAccessService.requireManager(scopeType, scopeId, userId);
        return subscriptionRepository.findByScopeTypeAndScopeId(scopeType, scopeId)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Abunəlik tapılmadı."));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionReceiptDto> receipts(ProviderScopeType scopeType, long scopeId, long userId) {
        scopeAccessService.requireManager(scopeType, scopeId, userId);
        ProviderSubscriptionEntity subscription = subscriptionRepository.findByScopeTypeAndScopeId(scopeType, scopeId)
                .orElse(null);
        if (subscription == null) return List.of();
        Stream<SubscriptionReceiptDto> legacyReceipts = paymentSessionRepository
                .findByProviderSubscriptionIdOrderByCreatedAtDesc(subscription.getId())
                .stream().map(this::legacyReceipt);
        Stream<SubscriptionReceiptDto> coinReceipts = coinPaymentRepository
                .findByProviderSubscriptionIdOrderByCreatedAtDescIdDesc(subscription.getId())
                .stream().map(this::coinReceipt);
        return Stream.concat(legacyReceipts, coinReceipts)
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .toList();
    }

    private SubscriptionReceiptDto legacyReceipt(PaymentSessionEntity session) {
        return new SubscriptionReceiptDto(
                session.getId(), session.getSubscriptionPlan().getCode(),
                session.getSubscriptionPlan().getBillingPeriod(), session.getStatus(), session.getAmount(),
                session.getCurrency(), session.getProvider(), session.getPaymentReference(),
                session.getCreatedAt(), session.getCompletedAt()
        );
    }

    private SubscriptionReceiptDto coinReceipt(SubscriptionCoinPaymentEntity payment) {
        return new SubscriptionReceiptDto(
                payment.getId(), payment.getSubscriptionPlan().getCode(),
                payment.getSubscriptionPlan().getBillingPeriod(), payment.getStatus(), payment.getAmount(),
                "COIN", "coin-wallet", payment.getPaymentReference(),
                payment.getCreatedAt(), payment.getCompletedAt()
        );
    }
}
