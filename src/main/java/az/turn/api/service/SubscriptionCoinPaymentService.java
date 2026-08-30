package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class SubscriptionCoinPaymentService {
    private final SubscriptionPlanRepository planRepository;
    private final ProviderSubscriptionRepository subscriptionRepository;
    private final SubscriptionCoinPaymentRepository coinPaymentRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletTransactionService walletTransactionService;
    private final ProviderScopeAccessService scopeAccessService;
    private final SubscriptionActivationService activationService;
    private final SubscriptionMapper mapper;
    private final UserRepository userRepository;
    private final Clock clock;

    public SubscriptionCoinPaymentService(
            SubscriptionPlanRepository planRepository,
            ProviderSubscriptionRepository subscriptionRepository,
            SubscriptionCoinPaymentRepository coinPaymentRepository,
            WalletTransactionRepository walletTransactionRepository,
            WalletTransactionService walletTransactionService,
            ProviderScopeAccessService scopeAccessService,
            SubscriptionActivationService activationService,
            SubscriptionMapper mapper,
            UserRepository userRepository,
            Clock clock
    ) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.coinPaymentRepository = coinPaymentRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.walletTransactionService = walletTransactionService;
        this.scopeAccessService = scopeAccessService;
        this.activationService = activationService;
        this.mapper = mapper;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public SubscriptionCoinPurchaseDto purchase(long userId, SubscriptionCoinPurchaseRequestDto request) {
        scopeAccessService.requireManager(request.scopeType(), request.scopeId(), userId);
        String planCode = request.planCode().trim().toUpperCase();
        String idempotencyKey = request.idempotencyKey().trim();
        SubscriptionPlanEntity plan = planRepository.findByCodeAndActiveTrue(planCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Abunəlik paketi tapılmadı."));
        requireMatchingScope(plan, request.scopeType());
        requireCoinPrice(plan);

        SubscriptionCoinPaymentEntity replay = coinPaymentRepository
                .findByPayerUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElse(null);
        if (replay != null) return replay(replay, request, planCode);

        WalletTransactionDto walletTransaction = walletTransactionService.apply(
                userId,
                walletCommand(userId, request, plan, idempotencyKey)
        );
        SubscriptionCoinPaymentEntity concurrentReplay = coinPaymentRepository
                .findByPayerUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElse(null);
        if (concurrentReplay != null) return replay(concurrentReplay, request, planCode);

        ProviderSubscriptionEntity subscription = subscriptionRepository
                .findByScopeTypeAndScopeId(request.scopeType(), request.scopeId())
                .orElseGet(() -> createSubscription(request, plan));
        ProviderSubscriptionEntity activated = activationService.activate(subscription.getId(), plan);
        LocalDateTime now = LocalDateTime.now(clock);
        SubscriptionCoinPaymentEntity payment = new SubscriptionCoinPaymentEntity();
        payment.setPayerUser(userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "İstifadəçi tapılmadı.")));
        payment.setProviderSubscription(activated);
        payment.setSubscriptionPlan(plan);
        payment.setWalletTransactionId(walletTransaction.id());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setAmount(plan.getCoinPrice());
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaymentReference("COIN-SUB-" + walletTransaction.id());
        payment.setCreatedAt(now);
        payment.setCompletedAt(now);
        return toDto(coinPaymentRepository.save(payment), walletTransaction.balanceAfter());
    }

    private SubscriptionCoinPurchaseDto replay(
            SubscriptionCoinPaymentEntity payment,
            SubscriptionCoinPurchaseRequestDto request,
            String planCode
    ) {
        boolean matches = payment.getProviderSubscription().getScopeType() == request.scopeType()
                && payment.getProviderSubscription().getScopeId() == request.scopeId()
                && payment.getSubscriptionPlan().getCode().equals(planCode);
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu ödəniş istinadı başqa abunəlik üçün istifadə olunub.");
        }
        WalletTransactionEntity transaction = walletTransactionRepository.findById(payment.getWalletTransactionId())
                .orElseThrow(() -> new IllegalStateException("Abunəlik coin əməliyyatı tapılmadı."));
        return toDto(payment, transaction.getBalanceAfter());
    }

    private WalletTransactionCommandDto walletCommand(
            long userId,
            SubscriptionCoinPurchaseRequestDto request,
            SubscriptionPlanEntity plan,
            String idempotencyKey
    ) {
        return new WalletTransactionCommandDto(
                WalletTransactionType.SUBSCRIPTION_PAYMENT,
                plan.getCoinPrice(),
                WalletActorType.USER,
                userId,
                null,
                "subscription:" + userId + ":" + idempotencyKey,
                plan.getName() + " · " + request.scopeType() + " #" + request.scopeId()
        );
    }

    private ProviderSubscriptionEntity createSubscription(
            SubscriptionCoinPurchaseRequestDto request,
            SubscriptionPlanEntity plan
    ) {
        ProviderSubscriptionEntity subscription = new ProviderSubscriptionEntity();
        subscription.setScopeType(request.scopeType());
        subscription.setScopeId(request.scopeId());
        subscription.setPlan(plan);
        subscription.setBillingPeriod(plan.getBillingPeriod());
        subscription.setStatus(SubscriptionStatus.PENDING_PAYMENT);
        subscription.setRoomLimit(plan.getRoomLimit());
        subscription.setEmployeeLimit(plan.getEmployeeLimit());
        return subscriptionRepository.save(subscription);
    }

    private SubscriptionCoinPurchaseDto toDto(SubscriptionCoinPaymentEntity payment, long balanceAfter) {
        return new SubscriptionCoinPurchaseDto(
                payment.getId(),
                payment.getWalletTransactionId(),
                payment.getAmount(),
                balanceAfter,
                payment.getPaymentReference(),
                mapper.toDto(payment.getProviderSubscription()),
                payment.getCompletedAt()
        );
    }

    private void requireMatchingScope(SubscriptionPlanEntity plan, ProviderScopeType scopeType) {
        if (plan.getScopeType() != scopeType) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu abunəlik paketi seçilən iş sahəsinə uyğun deyil.");
        }
    }

    private void requireCoinPrice(SubscriptionPlanEntity plan) {
        if (plan.getCoinPrice() == null || plan.getCoinPrice() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Abunəlik paketinin coin qiyməti təyin edilməyib.");
        }
    }
}
