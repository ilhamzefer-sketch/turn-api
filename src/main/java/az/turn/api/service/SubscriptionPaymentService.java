package az.turn.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SubscriptionPaymentService {
    private final SubscriptionPlanRepository planRepository;
    private final ProviderSubscriptionRepository subscriptionRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final ProviderScopeAccessService scopeAccessService;
    private final SubscriptionMapper mapper;
    private final SecureTokenService tokenService;
    private final Map<String, PaymentProvider> paymentProviders;
    private final String paymentMode;
    private final String paymentProviderName;
    private final Clock clock;

    public SubscriptionPaymentService(
            SubscriptionPlanRepository planRepository,
            ProviderSubscriptionRepository subscriptionRepository,
            PaymentSessionRepository paymentSessionRepository,
            ProviderScopeAccessService scopeAccessService,
            SubscriptionMapper mapper,
            SecureTokenService tokenService,
            List<PaymentProvider> paymentProviders,
            @Value("${app.payment.mode:sandbox}") String paymentMode,
            @Value("${app.payment.provider:mock}") String paymentProviderName,
            Clock clock
    ) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentSessionRepository = paymentSessionRepository;
        this.scopeAccessService = scopeAccessService;
        this.mapper = mapper;
        this.tokenService = tokenService;
        this.paymentProviders = paymentProviders.stream()
                .collect(Collectors.toMap(provider -> provider.providerName().toLowerCase(), Function.identity()));
        this.paymentMode = paymentMode;
        this.paymentProviderName = switch (paymentProviderName.toLowerCase()) {
            case "mock" -> "sandbox";
            case "test" -> "abb";
            default -> paymentProviderName.toLowerCase();
        };
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlanDto> plans() {
        return planRepository.findByActiveTrueOrderByAmountAsc().stream().map(mapper::toDto).toList();
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Abunəlik tapılmadı."));
        return paymentSessionRepository.findByProviderSubscriptionIdOrderByCreatedAtDesc(subscription.getId())
                .stream().map(this::receipt).toList();
    }

    @Transactional
    public SubscriptionPaymentSessionDto checkout(long userId, SubscriptionCheckoutRequestDto request) {
        scopeAccessService.requireManager(request.scopeType(), request.scopeId(), userId);
        SubscriptionPlanEntity plan = planRepository.findByCodeAndActiveTrue(request.planCode().trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Abunəlik paketi tapılmadı."));
        ProviderSubscriptionEntity subscription = subscriptionRepository
                .findByScopeTypeAndScopeId(request.scopeType(), request.scopeId())
                .orElseGet(() -> createPendingSubscription(request, plan));

        String rawToken = tokenService.generate();
        PaymentSessionEntity session = new PaymentSessionEntity();
        session.setSessionToken("sha256:" + tokenService.hash(rawToken));
        session.setProvider(paymentProviderName);
        session.setPaymentMode(paymentMode);
        session.setStatus(PaymentStatus.PENDING);
        session.setPaymentPurpose(PaymentPurpose.PROVIDER_SUBSCRIPTION);
        session.setProviderSubscription(subscription);
        session.setSubscriptionPlan(plan);
        session.setAmount(plan.getAmount());
        session.setCurrency(plan.getCurrency());
        session.setCardHolder(normalizeCardHolder(request.cardHolder()));
        session.setCardLast4(lastFour(request.cardNumber()));
        session.setSandboxOutcome(sandboxOutcome(request.cardNumber()));
        PaymentSessionEntity saved = paymentSessionRepository.save(session);
        PaymentProvider provider = resolveProvider(saved.getProvider());
        provider.initialize(saved);
        if ("sandbox".equalsIgnoreCase(saved.getProvider())) {
            saved.setStatus(provider.confirm(saved));
            if (saved.getStatus() == PaymentStatus.COMPLETED) activate(saved);
            saved.setCompletedAt(LocalDateTime.now(clock));
        }
        return toDto(paymentSessionRepository.save(saved), rawToken);
    }

    @Transactional(readOnly = true)
    public SubscriptionPaymentSessionDto getSession(long sessionId, String token, long userId) {
        PaymentSessionEntity session = requireSubscriptionSession(sessionId);
        validateToken(session, token);
        requireSubscriptionAccess(session, userId);
        return toDto(session, null);
    }

    @Transactional
    public SubscriptionPaymentSessionDto confirm(long sessionId, String token, long userId) {
        PaymentSessionEntity session = requireSubscriptionSessionForUpdate(sessionId);
        validateToken(session, token);
        requireSubscriptionAccess(session, userId);
        return confirmLocked(session);
    }

    @Transactional
    public void reconcile(long sessionId) {
        PaymentSessionEntity session = requireSubscriptionSessionForUpdate(sessionId);
        confirmLocked(session);
    }

    @Transactional
    public SubscriptionPaymentSessionDto cancel(long sessionId, String token, long userId) {
        PaymentSessionEntity session = requireSubscriptionSessionForUpdate(sessionId);
        validateToken(session, token);
        requireSubscriptionAccess(session, userId);
        if (session.getStatus() == PaymentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tamamlanmış ödəniş ləğv edilə bilməz.");
        }
        if ("birbank".equalsIgnoreCase(session.getProvider()) || "abb".equalsIgnoreCase(session.getProvider())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bank ödənişini bank səhifəsində ləğv edin.");
        }
        session.setStatus(PaymentStatus.CANCELLED);
        session.setCompletedAt(LocalDateTime.now(clock));
        session.setExternalOrderPassword(null);
        return toDto(paymentSessionRepository.save(session), null);
    }

    private SubscriptionPaymentSessionDto confirmLocked(PaymentSessionEntity session) {
        if (session.getStatus() == PaymentStatus.COMPLETED) return toDto(session, null);
        if (session.getStatus() == PaymentStatus.CANCELLED || session.getStatus() == PaymentStatus.FAILED) {
            return toDto(session, null);
        }
        PaymentStatus status = resolveProvider(session.getProvider()).confirm(session);
        session.setStatus(status);
        if (status == PaymentStatus.COMPLETED) activate(session);
        if (status != PaymentStatus.PENDING) {
            session.setCompletedAt(LocalDateTime.now(clock));
            session.setExternalOrderPassword(null);
        }
        return toDto(paymentSessionRepository.save(session), null);
    }

    private void activate(PaymentSessionEntity session) {
        ProviderSubscriptionEntity subscription = subscriptionRepository
                .findByIdForUpdate(session.getProviderSubscription().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Abunəlik tapılmadı."));
        SubscriptionPlanEntity plan = session.getSubscriptionPlan();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime base = subscription.getExpiresAt() != null && subscription.getExpiresAt().isAfter(now)
                ? subscription.getExpiresAt()
                : now;
        LocalDateTime expiresAt = plan.getBillingPeriod() == BillingPeriod.YEARLY
                ? base.plusYears(1)
                : base.plusMonths(1);
        subscription.setPlan(plan);
        subscription.setBillingPeriod(plan.getBillingPeriod());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setRoomLimit(plan.getRoomLimit());
        subscription.setEmployeeLimit(plan.getEmployeeLimit());
        if (subscription.getStartsAt() == null) subscription.setStartsAt(now);
        subscription.setExpiresAt(expiresAt);
        subscription.setGraceEndsAt(expiresAt.plusDays(7));
        subscriptionRepository.save(subscription);
    }

    private ProviderSubscriptionEntity createPendingSubscription(
            SubscriptionCheckoutRequestDto request,
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

    private PaymentSessionEntity requireSubscriptionSession(long id) {
        PaymentSessionEntity session = paymentSessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ödəniş sessiyası tapılmadı."));
        requireSubscriptionPurpose(session);
        return session;
    }

    private PaymentSessionEntity requireSubscriptionSessionForUpdate(long id) {
        PaymentSessionEntity session = paymentSessionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ödəniş sessiyası tapılmadı."));
        requireSubscriptionPurpose(session);
        return session;
    }

    private void requireSubscriptionPurpose(PaymentSessionEntity session) {
        if (session.getPaymentPurpose() != PaymentPurpose.PROVIDER_SUBSCRIPTION
                || session.getProviderSubscription() == null || session.getSubscriptionPlan() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ödəniş sessiyası tapılmadı.");
        }
    }

    private void requireSubscriptionAccess(PaymentSessionEntity session, long userId) {
        ProviderSubscriptionEntity subscription = session.getProviderSubscription();
        scopeAccessService.requireManager(subscription.getScopeType(), subscription.getScopeId(), userId);
    }

    private void validateToken(PaymentSessionEntity session, String token) {
        String supplied = "sha256:" + tokenService.hash(token == null ? "" : token);
        if (!MessageDigest.isEqual(
                session.getSessionToken().getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ödəniş sessiyası tapılmadı.");
        }
    }

    private PaymentProvider resolveProvider(String name) {
        PaymentProvider provider = paymentProviders.get(name.toLowerCase());
        if (provider == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ödəniş provayderi tapılmadı.");
        return provider;
    }

    private SubscriptionPaymentSessionDto toDto(PaymentSessionEntity session, String rawToken) {
        return new SubscriptionPaymentSessionDto(
                session.getId(), rawToken, session.getStatus(), session.getProvider(), session.getPaymentMode(),
                session.getAmount(), session.getCurrency(), session.getPaymentReference(), checkoutUrl(session),
                mapper.toDto(session.getProviderSubscription()), session.getCreatedAt(), session.getCompletedAt()
        );
    }

    private SubscriptionReceiptDto receipt(PaymentSessionEntity session) {
        return new SubscriptionReceiptDto(
                session.getId(), session.getSubscriptionPlan().getCode(),
                session.getSubscriptionPlan().getBillingPeriod(), session.getStatus(), session.getAmount(),
                session.getCurrency(), session.getProvider(), session.getPaymentReference(),
                session.getCreatedAt(), session.getCompletedAt()
        );
    }

    private String checkoutUrl(PaymentSessionEntity session) {
        if (session.getStatus() != PaymentStatus.PENDING || session.getExternalHppUrl() == null
                || session.getExternalOrderId() == null || session.getExternalOrderPassword() == null) return null;
        return session.getExternalHppUrl() + "?id=" + session.getExternalOrderId()
                + "&password=" + session.getExternalOrderPassword();
    }

    private String normalizeCardHolder(String value) { return value == null || value.isBlank() ? "E-Novbe" : value.trim(); }
    private String lastFour(String value) { return value == null || value.length() < 4 ? "" : value.substring(value.length() - 4); }
    private String sandboxOutcome(String value) { return value != null && value.endsWith("0002") ? "FAIL" : "SUCCESS"; }
}
