package az.turn.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QueueService {

    private static final long FERDI_PAYMENT_AMOUNT = 20;
    private static final long KORPORATIV_PAYMENT_AMOUNT = 100;
    private static final long DEFAULT_AVERAGE_SERVICE_MINUTES = 5;

    private final RegistrationRepository registrationRepository;
    private final CustomerRepository customerRepository;
    private final QueueRepository queueRepository;
    private final QueueManagerRepository queueManagerRepository;
    private final CustomerQueueEntryRepository customerQueueEntryRepository;
    private final GuestQueueEntryRepository guestQueueEntryRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final Map<String, PaymentProvider> paymentProviders;
    private final PasswordEncoder passwordEncoder;
    private final String paymentMode;
    private final String paymentProviderName;
    private final String adminUsername;
    private final String adminPasswordHash;

    public QueueService(
            RegistrationRepository registrationRepository,
            CustomerRepository customerRepository,
            QueueRepository queueRepository,
            QueueManagerRepository queueManagerRepository,
            CustomerQueueEntryRepository customerQueueEntryRepository,
            GuestQueueEntryRepository guestQueueEntryRepository,
            PaymentSessionRepository paymentSessionRepository,
            List<PaymentProvider> paymentProviders,
            PasswordEncoder passwordEncoder,
            @Value("${app.payment.mode:sandbox}") String paymentMode,
            @Value("${app.payment.provider:mock}") String paymentProviderName,
            @Value("${app.admin.username:admin}") String adminUsername,
            @Value("${app.admin.password-hash}") String adminPasswordHash
    ) {
        this.registrationRepository = registrationRepository;
        this.customerRepository = customerRepository;
        this.queueRepository = queueRepository;
        this.queueManagerRepository = queueManagerRepository;
        this.customerQueueEntryRepository = customerQueueEntryRepository;
        this.guestQueueEntryRepository = guestQueueEntryRepository;
        this.paymentSessionRepository = paymentSessionRepository;
        this.paymentProviders = paymentProviders.stream()
                .collect(Collectors.toMap(provider -> provider.providerName().toLowerCase(), Function.identity()));
        this.passwordEncoder = passwordEncoder;
        this.paymentMode = paymentMode;
        this.paymentProviderName = switch (paymentProviderName.toLowerCase()) {
            case "mock" -> "sandbox";
            case "test" -> "birbank";
            default -> paymentProviderName.toLowerCase();
        };
        this.adminUsername = adminUsername;
        this.adminPasswordHash = adminPasswordHash;
    }

    @Transactional
    public RegistrationResponse createRegistration(RegistrationRequest request) {
        String firstName = normalizeRequired(request.firstName(), "Ad mutleqdir.");
        String lastName = normalizeRequired(request.lastName(), "Soyad mutleqdir.");
        String email = normalizeRequired(request.email(), "Email mutleqdir.").toLowerCase();
        String password = normalizeRequired(request.password(), "Sifre mutleqdir.");
        String cardHolder = normalizeRequired(request.cardHolder(), "Kart sahibi mutleqdir.");
        String cardNumber = normalizeRequired(request.cardNumber(), "Kart nomresi mutleqdir.");
        String expireDate = normalizeRequired(request.expireDate(), "Kartin son istifade tarixi mutleqdir.");
        String cvv = normalizeRequired(request.cvv(), "CVV mutleqdir.");
        RegistrationType registrationType = request.registrationType();

        if (registrationType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qeydiyyat novu secilmelidir.");
        }
        if (!request.paid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qeydiyyat ucun mock odenis tamamlanmalidir.");
        }
        if (registrationRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu email ile qeydiyyat artiq movcuddur.");
        }

        validateMockPayment(cardHolder, cardNumber, expireDate, cvv);

        RegistrationEntity entity = new RegistrationEntity();
        entity.setFirstName(firstName);
        entity.setLastName(lastName);
        entity.setEmail(email);
        entity.setPasswordHash(passwordEncoder.encode(password));
        entity.setPaid(true);
        entity.setPaymentReference("MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        entity.setRegistrationType(registrationType);
        entity.setStatus(RegistrationStatus.ACTIVE);
        entity.setCreatedAt(LocalDateTime.now());

        return toRegistrationResponse(registrationRepository.save(entity));
    }

    @Transactional
    public PaymentSessionResponse createRegistrationPaymentSession(RegistrationPaymentSessionRequest request) {
        String firstName = normalizeRequired(request.firstName(), "Ad mutleqdir.");
        String lastName = normalizeRequired(request.lastName(), "Soyad mutleqdir.");
        String email = normalizeRequired(request.email(), "Email mutleqdir.").toLowerCase();
        String password = normalizeRequired(request.password(), "Sifre mutleqdir.");
        RegistrationType registrationType = request.registrationType();

        if (registrationType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qeydiyyat novu secilmelidir.");
        }
        RegistrationEntity registration = registrationRepository.findByEmail(email).orElse(null);
        if (registration != null && registration.getStatus() == RegistrationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu email ile qeydiyyat artiq movcuddur.");
        }

        String cardHolder = normalizeCardHolder(request.cardHolder(), firstName, lastName);
        String cardNumber = request.cardNumber() == null ? "" : request.cardNumber().trim();

        if (registration == null) {
            registration = new RegistrationEntity();
        }
        registration.setFirstName(firstName);
        registration.setLastName(lastName);
        registration.setEmail(email);
        registration.setPasswordHash(passwordEncoder.encode(password));
        registration.setPaid(false);
        registration.setPaymentReference("PENDING-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        registration.setRegistrationType(registrationType);
        registration.setStatus(RegistrationStatus.PENDING_PAYMENT);
        registration.setCreatedAt(LocalDateTime.now());
        RegistrationEntity savedRegistration = registrationRepository.save(registration);

        String sessionToken = UUID.randomUUID().toString();
        PaymentSessionEntity session = new PaymentSessionEntity();
        session.setSessionToken(hashPaymentSessionToken(sessionToken));
        session.setProvider(paymentProviderName);
        session.setPaymentMode(paymentMode);
        session.setStatus(PaymentStatus.PENDING);
        session.setRegistrationType(registrationType);
        session.setAmount(resolvePaymentAmount(registrationType));
        session.setCurrency("AZN");
        session.setFirstName(firstName);
        session.setLastName(lastName);
        session.setEmail(email);
        session.setPasswordHash(passwordEncoder.encode(password));
        session.setCardHolder(cardHolder);
        session.setCardLast4(resolveCardLast4(cardNumber));
        session.setSandboxOutcome(resolveSandboxOutcome(cardNumber));
        session.setRegistration(savedRegistration);
        PaymentSessionEntity savedSession = paymentSessionRepository.save(session);
        PaymentProvider paymentProvider = paymentProviders.get(savedSession.getProvider().toLowerCase());
        if (paymentProvider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment provider tapilmadi.");
        }
        paymentProvider.initialize(savedSession);
        return toPaymentSessionResponse(paymentSessionRepository.save(savedSession), sessionToken);
    }

    @Transactional(readOnly = true)
    public PaymentSessionResponse getPaymentSession(long paymentSessionId, String sessionToken) {
        PaymentSessionEntity session = findPaymentSession(paymentSessionId);
        validatePaymentSessionToken(session, sessionToken);
        return toPaymentSessionResponse(session);
    }

    @Transactional
    public PaymentConfirmationResponse confirmRegistrationPayment(long paymentSessionId, String sessionToken) {
        return processPaymentConfirmation(paymentSessionId, sessionToken, true);
    }

    @Transactional
    public void reconcilePendingPayment(long paymentSessionId) {
        processPaymentConfirmation(paymentSessionId, null, false);
    }

    private PaymentConfirmationResponse processPaymentConfirmation(long paymentSessionId, String sessionToken, boolean issueAuthentication) {
        PaymentSessionEntity session = paymentSessionRepository.findByIdForUpdate(paymentSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ödəniş sessiyası tapılmadı."));
        if (issueAuthentication) {
            validatePaymentSessionToken(session, sessionToken);
        }

        if (session.getStatus() == PaymentStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Odenis sessiyasi legv olunub.");
        }

        if (session.getStatus() == PaymentStatus.COMPLETED) {
            if (!issueAuthentication || session.getAuthenticationIssuedAt() != null) {
                return new PaymentConfirmationResponse(toPaymentSessionResponse(session), null);
            }
            RegistrationEntity existing = getOrCreateSessionRegistration(session);
            session.setAuthenticationIssuedAt(LocalDateTime.now());
            paymentSessionRepository.save(session);
            return new PaymentConfirmationResponse(toPaymentSessionResponse(session), toRegistrationResponse(existing));
        }

        PaymentProvider paymentProvider = paymentProviders.get(session.getProvider().toLowerCase());
        if (paymentProvider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment provider tapilmadi.");
        }

        PaymentStatus result = paymentProvider.confirm(session);
        session.setStatus(result);
        if (result == PaymentStatus.COMPLETED || result == PaymentStatus.FAILED || result == PaymentStatus.CANCELLED) {
            session.setCompletedAt(LocalDateTime.now());
            session.setExternalOrderPassword(null);
        }

        if (result == PaymentStatus.PENDING) {
            paymentSessionRepository.save(session);
            return new PaymentConfirmationResponse(toPaymentSessionResponse(session), null);
        }

        if (result != PaymentStatus.COMPLETED) {
            RegistrationEntity registration = findSessionRegistration(session);
            if (registration != null) {
                expireRegistration(registration);
            }
            if (session.getPaymentReference() == null || session.getPaymentReference().isBlank()) {
                session.setPaymentReference("PAY-FAILED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }
            PaymentSessionEntity savedFailedSession = paymentSessionRepository.save(session);
            return new PaymentConfirmationResponse(toPaymentSessionResponse(savedFailedSession), null);
        }

        RegistrationEntity entity = getOrCreateSessionRegistration(session);
        entity.setPaid(true);
        entity.setStatus(RegistrationStatus.ACTIVE);
        entity.setPaymentReference(session.getPaymentReference() == null || session.getPaymentReference().isBlank()
                ? "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
                : session.getPaymentReference());

        RegistrationEntity savedRegistration = registrationRepository.save(entity);
        session.setPaymentReference(savedRegistration.getPaymentReference());
        if (issueAuthentication) {
            session.setAuthenticationIssuedAt(LocalDateTime.now());
        }
        PaymentSessionEntity savedSession = paymentSessionRepository.save(session);
        return new PaymentConfirmationResponse(toPaymentSessionResponse(savedSession),
                issueAuthentication ? toRegistrationResponse(savedRegistration) : null);
    }

    @Transactional
    public PaymentSessionResponse cancelRegistrationPayment(long paymentSessionId, String sessionToken) {
        PaymentSessionEntity session = paymentSessionRepository.findByIdForUpdate(paymentSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ödəniş sessiyası tapılmadı."));
        validatePaymentSessionToken(session, sessionToken);
        if ("birbank".equalsIgnoreCase(session.getProvider())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bank ödənişi tətbiqdən ləğv edilmir. Bank səhifəsində imtina edin.");
        }
        if (session.getStatus() == PaymentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tamamlanmis odenis sessiyasini legv etmek olmaz.");
        }
        session.setStatus(PaymentStatus.CANCELLED);
        session.setCompletedAt(LocalDateTime.now());
        session.setExternalOrderPassword(null);
        RegistrationEntity registration = findSessionRegistration(session);
        if (registration != null) {
            expireRegistration(registration);
        }
        return toPaymentSessionResponse(paymentSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public RegistrationResponse login(LoginRequest request) {
        String email = normalizeRequired(request.email(), "Email mutleqdir.").toLowerCase();
        String password = normalizeRequired(request.password(), "Sifre mutleqdir.");

        RegistrationEntity registration = registrationRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ve ya sifre sehvdir."));

        if (!passwordEncoder.matches(password, registration.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ve ya sifre sehvdir.");
        }
        if (registration.getStatus() != RegistrationStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Profil aktiv deyil. Odenis tamamlanmayib ve ya profil expired olub.");
        }

        return toRegistrationResponse(registration);
    }

    @Transactional
    public CustomerResponse registerCustomer(CustomerRegistrationRequest request) {
        String firstName = normalizeRequired(request.firstName(), "Ad mutleqdir.");
        String lastName = normalizeRequired(request.lastName(), "Soyad mutleqdir.");
        String email = normalizeRequired(request.email(), "Email mutleqdir.").toLowerCase();
        String password = normalizeRequired(request.password(), "Sifre mutleqdir.");

        if (customerRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu email ile musteri artiq movcuddur.");
        }

        CustomerEntity entity = new CustomerEntity();
        entity.setFirstName(firstName);
        entity.setLastName(lastName);
        entity.setEmail(email);
        entity.setPasswordHash(passwordEncoder.encode(password));
        entity.setCreatedAt(LocalDateTime.now());
        return toCustomerResponse(customerRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public CustomerResponse loginCustomer(CustomerLoginRequest request) {
        String email = normalizeRequired(request.email(), "Email mutleqdir.").toLowerCase();
        String password = normalizeRequired(request.password(), "Sifre mutleqdir.");

        CustomerEntity customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ve ya sifre sehvdir."));

        if (!passwordEncoder.matches(password, customer.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ve ya sifre sehvdir.");
        }

        return toCustomerResponse(customer);
    }

    @Transactional
    public QueueResponse createQueue(QueueCreateRequest request) {
        RegistrationEntity registration = registrationRepository.findById(request.registrationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Qeydiyyat tapilmadi."));

        String address = normalizeRequired(request.address(), "Unvan mutleqdir.");
        String serviceName = normalizeRequired(request.serviceName(), "Isin adi mutleqdir.");
        List<String> categories = sanitizeCategories(request.categories());

        if (categories.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "En azi bir kateqoriya elave edin.");
        }

        if (registration.getRegistrationType() == RegistrationType.FERDI
                && queueRepository.countByRegistrationId(registration.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ferdi qeydiyyat yalniz bir novbe yarada biler.");
        }

        QueueEntity queue = new QueueEntity();
        queue.setRegistration(registration);
        queue.setAddress(address);
        queue.setServiceName(serviceName);
        queue.setCategories(categories);
        queue.setQrToken(UUID.randomUUID().toString());
        queue.setCurrentServingNumber(0);
        queue.setLastIssuedNumber(0);
        queue.setAverageServiceMinutes(DEFAULT_AVERAGE_SERVICE_MINUTES);
        queue.setServedCustomersCount(0);
        queue.setTotalServiceMinutes(0);
        queue.setLastAdvancedAt(null);
        queue.setResetMode(resolveResetMode(request.resetMode()));
        queue.setResetAt(resolveResetAt(queue.getResetMode(), request.resetAt()));
        queue.setActive(true);

        QueueEntity savedQueue = queueRepository.save(queue);

        if (registration.getRegistrationType() == RegistrationType.KORPORATIV) {
            String managerUsername = normalizeRequired(request.managerUsername(), "Korporativ novbe ucun queue user username mutleqdir.").toLowerCase();
            String managerPassword = normalizeRequired(request.managerPassword(), "Korporativ novbe ucun queue user password mutleqdir.");

            if (!managerUsername.matches("[A-Za-z0-9._-]{3,100}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "İdarəçi adı 3-100 simvol olmalı və yalnız hərf, rəqəm, nöqtə, tire və alt xətdən ibarət olmalıdır.");
            }
            if (managerPassword.length() < 8 || managerPassword.length() > 72) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "İdarəçi şifrəsi 8-72 simvol olmalıdır.");
            }

            if (queueManagerRepository.existsByUsername(managerUsername)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu queue user username artiq movcuddur.");
            }

            QueueManagerEntity manager = new QueueManagerEntity();
            manager.setQueue(savedQueue);
            manager.setUsername(managerUsername);
            manager.setPasswordHash(passwordEncoder.encode(managerPassword));
            queueManagerRepository.save(manager);
        }

        return toQueueResponse(savedQueue);
    }

    @Transactional
    public List<QueueResponse> getQueues(long registrationId) {
        if (!registrationRepository.existsById(registrationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Qeydiyyat tapilmadi.");
        }

        return queueRepository.findByRegistrationIdOrderByIdAsc(registrationId)
                .stream()
                .map(this::ensureQueueState)
                .map(this::toQueueResponse)
                .toList();
    }

    @Transactional
    public List<QueueResponse> getPublicQueues() {
        return queueRepository.findAll()
                .stream()
                .map(this::ensureQueueState)
                .filter(QueueEntity::isActive)
                .sorted(Comparator.comparing(QueueEntity::getId).reversed())
                .map(this::toQueueResponse)
                .toList();
    }

    @Transactional
    public QueueDetailResponse getQueueDetail(long queueId, Long registrationId, Long queueManagerId) {
        QueueEntity queue = ensureQueueState(findQueue(queueId));
        validateQueueAccess(queue, registrationId, queueManagerId);
        return toQueueDetailResponse(queue);
    }

    @Transactional
    public QueueScanResponse scanQueue(QueueScanRequest request) {
        String qrToken = normalizeRequired(request.qrToken(), "QR token mutleqdir.");
        QueueEntity queue = queueRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bu QR token ucun novbe tapilmadi."));
        queue = ensureQueueState(queue);
        if (!queue.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu novbe artiq deaktivdir.");
        }
        QueueJoinResult result = createQueueEntry(queue, request.customerId(), request.displayName(), request.firstName(), request.lastName());

        return new QueueScanResponse(
                result.queue().getId(),
                result.queue().getAddress(),
                result.queue().getServiceName(),
                copyCategories(result.queue()),
                result.queue().getRegistration().getFullName(),
                result.queueNumber(),
                result.queue().getCurrentServingNumber(),
                result.waitingCount(),
                result.queue().getLastIssuedNumber(),
                result.estimatedWaitMinutes(),
                resolveAverageServiceMinutes(result.queue()),
                result.queue().getQrToken(),
                buildScanMessage(result.queueNumber(), result.queue().getCurrentServingNumber(), result.waitingCount(), result.estimatedWaitMinutes())
        );
    }

    @Transactional
    public CustomerQueueJoinResponse joinQueue(CustomerQueueJoinRequest request) {
        if (request.customerId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evdan qosulmaq ucun musteri kimi daxil olun.");
        }
        QueueEntity queue = ensureQueueState(resolveQueueForJoin(request));
        if (!queue.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu novbe artiq deaktivdir.");
        }
        QueueJoinResult result = createQueueEntry(queue, request.customerId(), request.displayName(), null, null);

        return new CustomerQueueJoinResponse(
                result.entry() != null ? result.entry().getId() : null,
                result.entry() != null ? result.entry().getCustomer().getId() : null,
                result.queue().getId(),
                result.queue().getAddress(),
                result.queue().getServiceName(),
                copyCategories(result.queue()),
                result.queue().getRegistration().getFullName(),
                result.queueNumber(),
                result.queue().getCurrentServingNumber(),
                result.waitingCount(),
                result.queue().getLastIssuedNumber(),
                result.estimatedWaitMinutes(),
                resolveAverageServiceMinutes(result.queue()),
                result.queue().getQrToken(),
                result.entry() != null ? result.entry().getDisplayName() : null,
                buildScanMessage(result.queueNumber(), result.queue().getCurrentServingNumber(), result.waitingCount(), result.estimatedWaitMinutes())
        );
    }

    @Transactional
    public QueueDetailResponse advanceQueue(long queueId, QueueAdvanceRequest request) {
        QueueEntity queue = ensureQueueState(findQueue(queueId));
        validateQueueAccess(queue, request.registrationId(), request.queueManagerId());
        if (!queue.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deaktiv novbeni idare etmek olmur.");
        }

        if (queue.getCurrentServingNumber() >= queue.getLastIssuedNumber()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Novbede gozleyen yoxdur.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (queue.getCurrentServingNumber() > 0 && queue.getLastAdvancedAt() != null) {
            long elapsedMinutes = Math.max(1, Duration.between(queue.getLastAdvancedAt(), now).toMinutes());
            queue.setTotalServiceMinutes(queue.getTotalServiceMinutes() + elapsedMinutes);
            queue.setServedCustomersCount(queue.getServedCustomersCount() + 1);
            queue.setAverageServiceMinutes(Math.max(1, queue.getTotalServiceMinutes() / queue.getServedCustomersCount()));
        }

        queue.setCurrentServingNumber(queue.getCurrentServingNumber() + 1);
        queue.setLastAdvancedAt(now);

        return toQueueDetailResponse(queueRepository.save(queue));
    }

    @Transactional
    public QueueDetailResponse resetQueue(long queueId, QueueResetRequest request) {
        QueueEntity queue = ensureQueueState(findQueue(queueId));
        validateQueueAccess(queue, request.registrationId(), request.queueManagerId());
        queue.setCurrentServingNumber(0);
        queue.setLastIssuedNumber(0);
        queue.setServedCustomersCount(0);
        queue.setTotalServiceMinutes(0);
        queue.setAverageServiceMinutes(DEFAULT_AVERAGE_SERVICE_MINUTES);
        queue.setLastAdvancedAt(null);
        if (queue.getResetMode() == QueueResetMode.MANUAL) {
            queue.setActive(true);
        }
        return toQueueDetailResponse(queueRepository.save(queue));
    }

    @Transactional
    public QueueManagerLoginResponse loginQueueManager(QueueManagerLoginRequest request) {
        String username = normalizeRequired(request.username(), "Queue user username mutleqdir.").toLowerCase();
        String password = normalizeRequired(request.password(), "Queue user password mutleqdir.");

        QueueManagerEntity manager = queueManagerRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Queue user username ve ya password sehvdir."));

        if (!passwordEncoder.matches(password, manager.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Queue user username ve ya password sehvdir.");
        }

        return new QueueManagerLoginResponse(
                manager.getId(),
                manager.getUsername(),
                toQueueDetailResponse(ensureQueueState(manager.getQueue())),
                null
        );
    }

    @Transactional
    public List<CustomerQueueHistoryItemResponse> getCustomerHistory(long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Musteri tapilmadi.");
        }

        return customerQueueEntryRepository.findByCustomerIdOrderByJoinedAtDesc(customerId)
                .stream()
                .map(entry -> {
                    ensureQueueState(entry.getQueue());
                    return toCustomerQueueHistoryItemResponse(entry);
                })
                .toList();
    }

    @Transactional
    public CustomerQueueEntryResponse renameCustomerQueueEntry(long entryId, CustomerQueueRenameRequest request) {
        CustomerQueueEntryEntity entry = findCustomerQueueEntry(entryId);
        validateCustomerQueueEntryAccess(entry, request.customerId());
        entry.setDisplayName(normalizeRequired(request.displayName(), "Novbe adi mutleqdir."));
        return toCustomerQueueEntryResponse(customerQueueEntryRepository.save(entry));
    }

    @Transactional
    public CustomerQueueEntryResponse rateCustomerQueueEntry(long entryId, CustomerQueueRatingRequest request) {
        CustomerQueueEntryEntity entry = findCustomerQueueEntry(entryId);
        validateCustomerQueueEntryAccess(entry, request.customerId());

        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qiymet 1 ile 5 arasi olmalidir.");
        }

        entry.setRating(request.rating());
        entry.setRatingNote(request.note() == null ? null : request.note().trim());
        return toCustomerQueueEntryResponse(customerQueueEntryRepository.save(entry));
    }

    private RegistrationResponse toRegistrationResponse(RegistrationEntity entity) {
        return new RegistrationResponse(
                entity.getId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getEmail(),
                entity.isPaid(),
                entity.getPaymentReference(),
                entity.getRegistrationType(),
                entity.getStatus(),
                entity.getCreatedAt(),
                null
        );
    }

    private CustomerResponse toCustomerResponse(CustomerEntity entity) {
        return new CustomerResponse(
                entity.getId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getEmail(),
                entity.getCreatedAt(),
                null
        );
    }

    private PaymentSessionResponse toPaymentSessionResponse(PaymentSessionEntity entity) {
        return toPaymentSessionResponse(entity, null);
    }

    private PaymentSessionResponse toPaymentSessionResponse(PaymentSessionEntity entity, String sessionToken) {
        return new PaymentSessionResponse(
                entity.getId(),
                sessionToken,
                entity.getProvider(),
                entity.getPaymentMode(),
                entity.getStatus(),
                entity.getRegistrationType(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getCardHolder(),
                entity.getCardLast4(),
                entity.getPaymentReference(),
                entity.getExternalOrderId(),
                buildCheckoutUrl(entity),
                entity.getCreatedAt(),
                entity.getCompletedAt()
        );
    }

    private String buildCheckoutUrl(PaymentSessionEntity entity) {
        if (entity.getStatus() != PaymentStatus.PENDING
                || entity.getExternalHppUrl() == null || entity.getExternalHppUrl().isBlank()
                || entity.getExternalOrderId() == null || entity.getExternalOrderPassword() == null
                || entity.getExternalOrderPassword().isBlank()) {
            return null;
        }
        return entity.getExternalHppUrl() + "?id=" + entity.getExternalOrderId() + "&password=" + entity.getExternalOrderPassword();
    }

    @Transactional(readOnly = true)
    public AdminLoginResponse loginAdmin(AdminLoginRequest request) {
        String username = normalizeRequired(request.username(), "Admin istifadeci adi mutleqdir.");
        String password = normalizeRequired(request.password(), "Admin sifresi mutleqdir.");

        if (!adminUsername.equals(username) || !passwordEncoder.matches(password, adminPasswordHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin username ve ya password sehvdir.");
        }

        return new AdminLoginResponse(adminUsername, "ADMIN", "Admin panelinə uğurla daxil oldunuz.", null);
    }

    @Transactional
    public AdminDashboardResponse getAdminDashboard(String search, String registrationType, String paymentStatus, String month) {
        List<RegistrationEntity> registrations = registrationRepository.findAll();
        List<QueueEntity> queues = queueRepository.findAll().stream().map(this::ensureQueueState).toList();
        List<PaymentSessionEntity> paymentSessions = paymentSessionRepository.findAll();
        Map<Long, Long> queueCountByRegistrationId = queues.stream()
                .collect(Collectors.groupingBy(queue -> queue.getRegistration().getId(), Collectors.counting()));

        List<AdminRegistrationItemResponse> allItems = registrations.stream()
                .map(registration -> toAdminRegistrationItem(registration, queueCountByRegistrationId.getOrDefault(registration.getId(), 0L)))
                .sorted(Comparator.comparing(AdminRegistrationItemResponse::createdAt).reversed())
                .toList();

        List<AdminRegistrationItemResponse> filteredItems = allItems.stream()
                .filter(item -> matchesSearch(item, search))
                .filter(item -> matchesRegistrationType(item, registrationType))
                .filter(item -> matchesPaymentStatus(item, paymentStatus))
                .filter(item -> matchesMonth(item, month))
                .toList();

        long totalRevenue = paymentSessions.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.COMPLETED)
                .mapToLong(PaymentSessionEntity::getAmount)
                .sum();

        AdminSummaryResponse summary = new AdminSummaryResponse(
                registrations.size(),
                registrations.stream().filter(RegistrationEntity::isPaid).count(),
                queues.size(),
                totalRevenue,
                customerRepository.count(),
                queues.stream().filter(QueueEntity::isActive).count(),
                registrations.stream().filter(item -> item.getStatus() == RegistrationStatus.EXPIRED).count(),
                paymentSessions.stream().filter(item -> item.getStatus() == PaymentStatus.PENDING).count(),
                paymentSessions.stream().filter(item -> item.getStatus() == PaymentStatus.COMPLETED).count()
        );

        List<AdminMonthlyPaymentResponse> monthlyPayments = paymentSessions.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.COMPLETED && payment.getCompletedAt() != null)
                .collect(Collectors.groupingBy(
                        payment -> YearMonth.from(payment.getCompletedAt()),
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<YearMonth, List<PaymentSessionEntity>>comparingByKey().reversed())
                .map(entry -> new AdminMonthlyPaymentResponse(
                        entry.getKey().toString(),
                        entry.getValue().size(),
                        entry.getValue().stream().mapToLong(PaymentSessionEntity::getAmount).sum()
                ))
                .toList();

        List<AdminPaymentItemResponse> recentPayments = paymentSessions.stream()
                .sorted(Comparator.comparing(PaymentSessionEntity::getCreatedAt).reversed())
                .limit(50)
                .map(payment -> new AdminPaymentItemResponse(
                        payment.getId(),
                        payment.getFirstName() + " " + payment.getLastName(),
                        payment.getEmail(),
                        payment.getRegistrationType(),
                        payment.getStatus(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getProvider(),
                        payment.getExternalOrderId(),
                        payment.getPaymentReference(),
                        payment.getCreatedAt(),
                        payment.getCompletedAt()
                ))
                .toList();

        List<AdminQueueItemResponse> queueItems = queues.stream()
                .sorted(Comparator.comparing(QueueEntity::getId).reversed())
                .map(queue -> new AdminQueueItemResponse(
                        queue.getId(),
                        queue.getServiceName(),
                        queue.getAddress(),
                        queue.getRegistration().getFullName(),
                        queue.getRegistration().getRegistrationType(),
                        queue.getCurrentServingNumber(),
                        queue.getLastIssuedNumber(),
                        Math.max(0, queue.getLastIssuedNumber() - queue.getCurrentServingNumber()),
                        resolveAverageServiceMinutes(queue),
                        queue.getResetMode(),
                        queue.getResetAt(),
                        queue.isActive()
                ))
                .toList();

        return new AdminDashboardResponse(summary, monthlyPayments, filteredItems, recentPayments, queueItems);
    }

    private List<String> copyCategories(QueueEntity entity) {
        return entity.getCategories() == null ? List.of() : List.copyOf(entity.getCategories());
    }

    private QueueResponse toQueueResponse(QueueEntity entity) {
        String managerUsername = queueManagerRepository.findByQueueId(entity.getId())
                .map(QueueManagerEntity::getUsername)
                .orElse(null);

        return new QueueResponse(
                entity.getId(),
                entity.getRegistration().getId(),
                entity.getRegistration().getRegistrationType(),
                entity.getRegistration().getFullName(),
                entity.getRegistration().getEmail(),
                entity.getAddress(),
                entity.getServiceName(),
                copyCategories(entity),
                entity.getQrToken(),
                managerUsername,
                entity.getResetMode(),
                entity.getResetAt(),
                entity.isActive()
        );
    }

    private CustomerQueueHistoryItemResponse toCustomerQueueHistoryItemResponse(CustomerQueueEntryEntity entry) {
        QueueEntity queue = entry.getQueue();
        long waitingAhead = Math.max(0, entry.getQueueNumber() - queue.getCurrentServingNumber() - 1);
        return new CustomerQueueHistoryItemResponse(
                entry.getId(),
                queue.getId(),
                entry.getDisplayName() == null || entry.getDisplayName().isBlank() ? queue.getServiceName() : entry.getDisplayName(),
                queue.getServiceName(),
                queue.getAddress(),
                copyCategories(queue),
                entry.getQueueNumber(),
                queue.getCurrentServingNumber(),
                waitingAhead,
                resolveAverageServiceMinutes(queue),
                entry.getRating(),
                entry.getRatingNote(),
                entry.getJoinedAt()
        );
    }

    private CustomerQueueEntryResponse toCustomerQueueEntryResponse(CustomerQueueEntryEntity entry) {
        return new CustomerQueueEntryResponse(
                entry.getId(),
                entry.getDisplayName(),
                entry.getRating(),
                entry.getRatingNote(),
                entry.getJoinedAt()
        );
    }

    private QueueDetailResponse toQueueDetailResponse(QueueEntity entity) {
        long averageServiceMinutes = resolveAverageServiceMinutes(entity);
        long waitingCount = Math.max(0, entity.getLastIssuedNumber() - entity.getCurrentServingNumber());
        long estimatedWaitMinutes = waitingCount * averageServiceMinutes;
        String managerUsername = queueManagerRepository.findByQueueId(entity.getId())
                .map(QueueManagerEntity::getUsername)
                .orElse(null);

        return new QueueDetailResponse(
                entity.getId(),
                entity.getRegistration().getId(),
                entity.getRegistration().getRegistrationType(),
                entity.getRegistration().getFullName(),
                entity.getRegistration().getEmail(),
                entity.getAddress(),
                entity.getServiceName(),
                copyCategories(entity),
                entity.getQrToken(),
                entity.getCurrentServingNumber(),
                entity.getLastIssuedNumber(),
                waitingCount,
                entity.getLastIssuedNumber(),
                averageServiceMinutes,
                estimatedWaitMinutes,
                entity.getLastAdvancedAt(),
                managerUsername,
                entity.getResetMode(),
                entity.getResetAt(),
                entity.isActive()
        );
    }

    private String normalizeRequired(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return value.trim();
    }

    private List<String> sanitizeCategories(List<String> categories) {
        if (categories == null) {
            return List.of();
        }

        return categories.stream()
                .filter(category -> category != null && !category.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private void validateMockPayment(String cardHolder, String cardNumber, String expireDate, String cvv) {
        if (cardHolder.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kart sahibinin adi qisa ola bilmez.");
        }
        String cleanCardNumber = cardNumber.replaceAll("\\s+", "");
        if (!cleanCardNumber.matches("\\d{16}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kart nomresi 16 reqem olmalidir.");
        }
        if (!expireDate.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tarix MM/YY formatinda olmalidir.");
        }
        if (!cvv.matches("\\d{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CVV 3 reqem olmalidir.");
        }
    }

    private String resolveSandboxOutcome(String cardNumber) {
        String cleanCardNumber = cardNumber.replaceAll("\\s+", "");
        return cleanCardNumber.endsWith("0") ? "FAIL" : "SUCCESS";
    }

    private String resolveCardLast4(String cardNumber) {
        String cleanCardNumber = cardNumber.replaceAll("\\s+", "");
        if (cleanCardNumber.length() >= 4) {
            return cleanCardNumber.substring(cleanCardNumber.length() - 4);
        }
        return "0000";
    }

    private String normalizeCardHolder(String cardHolder, String firstName, String lastName) {
        if (cardHolder != null && !cardHolder.isBlank()) {
            return cardHolder.trim();
        }
        return firstName + " " + lastName;
    }

    private AdminRegistrationItemResponse toAdminRegistrationItem(RegistrationEntity entity, long queueCount) {
        return new AdminRegistrationItemResponse(
                entity.getId(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getRegistrationType(),
                entity.getStatus(),
                entity.isPaid(),
                entity.getPaymentReference(),
                resolvePaymentAmount(entity),
                queueCount,
                entity.getCreatedAt()
        );
    }

    private long resolvePaymentAmount(RegistrationEntity entity) {
        return entity.getRegistrationType() == RegistrationType.KORPORATIV
                ? KORPORATIV_PAYMENT_AMOUNT
                : FERDI_PAYMENT_AMOUNT;
    }

    private long resolvePaymentAmount(RegistrationType registrationType) {
        return registrationType == RegistrationType.KORPORATIV
                ? KORPORATIV_PAYMENT_AMOUNT
                : FERDI_PAYMENT_AMOUNT;
    }

    private long resolveAverageServiceMinutes(QueueEntity queue) {
        return Math.max(1, queue.getAverageServiceMinutes() == 0 ? DEFAULT_AVERAGE_SERVICE_MINUTES : queue.getAverageServiceMinutes());
    }

    private QueueEntity findQueue(long queueId) {
        return queueRepository.findById(queueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Novbe tapilmadi."));
    }

    private PaymentSessionEntity findPaymentSession(long paymentSessionId) {
        return paymentSessionRepository.findById(paymentSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Odenis sessiyasi tapilmadi."));
    }

    private void validatePaymentSessionToken(PaymentSessionEntity session, String suppliedToken) {
        String storedToken = session.getSessionToken();
        String comparableToken = storedToken != null && storedToken.startsWith("sha256:")
                ? hashPaymentSessionToken(suppliedToken == null ? "" : suppliedToken)
                : suppliedToken;
        byte[] expected = storedToken == null ? new byte[0] : storedToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = comparableToken == null ? new byte[0] : comparableToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ödəniş sessiyası tapılmadı.");
        }
    }

    private String hashPaymentSessionToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private RegistrationEntity findSessionRegistration(PaymentSessionEntity session) {
        if (session.getRegistration() != null) {
            return session.getRegistration();
        }
        if (session.getEmail() == null || session.getEmail().isBlank()) {
            return null;
        }
        RegistrationEntity registration = registrationRepository.findByEmail(session.getEmail().trim().toLowerCase()).orElse(null);
        if (registration != null) {
            session.setRegistration(registration);
        }
        return registration;
    }

    private RegistrationEntity getOrCreateSessionRegistration(PaymentSessionEntity session) {
        RegistrationEntity existing = findSessionRegistration(session);
        if (existing != null) {
            return existing;
        }

        RegistrationEntity registration = new RegistrationEntity();
        registration.setFirstName(session.getFirstName());
        registration.setLastName(session.getLastName());
        registration.setEmail(session.getEmail().trim().toLowerCase());
        registration.setPasswordHash(session.getPasswordHash());
        registration.setPaid(false);
        registration.setPaymentReference("PENDING-LEGACY-" + session.getId());
        registration.setRegistrationType(session.getRegistrationType());
        registration.setStatus(RegistrationStatus.PENDING_PAYMENT);
        registration.setCreatedAt(session.getCreatedAt() == null ? LocalDateTime.now() : session.getCreatedAt());
        RegistrationEntity savedRegistration = registrationRepository.save(registration);
        session.setRegistration(savedRegistration);
        return savedRegistration;
    }

    private CustomerQueueEntryEntity findCustomerQueueEntry(long entryId) {
        return customerQueueEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Musteri novbe kaydi tapilmadi."));
    }

    private QueueEntity resolveQueueForJoin(CustomerQueueJoinRequest request) {
        if (request.queueId() != null) {
            return findQueue(request.queueId());
        }
        if (request.qrToken() != null && !request.qrToken().isBlank()) {
            return queueRepository.findByQrToken(request.qrToken().trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bu QR token ucun novbe tapilmadi."));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Queue ID ve ya QR token mutleqdir.");
    }

    private QueueJoinResult createQueueEntry(QueueEntity queue, Long customerId, String displayName, String guestFirstName, String guestLastName) {
        queue.setLastIssuedNumber(queue.getLastIssuedNumber() + 1);
        QueueEntity updatedQueue = queueRepository.save(queue);

        long queueNumber = updatedQueue.getLastIssuedNumber();
        long waitingCount = Math.max(0, updatedQueue.getLastIssuedNumber() - updatedQueue.getCurrentServingNumber());
        long waitingBeforeThisCustomer = Math.max(0, queueNumber - updatedQueue.getCurrentServingNumber() - 1);
        long estimatedWaitMinutes = waitingBeforeThisCustomer * resolveAverageServiceMinutes(updatedQueue);

        CustomerQueueEntryEntity savedEntry = null;
        if (customerId != null) {
            CustomerEntity customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Musteri tapilmadi."));

            CustomerQueueEntryEntity entry = new CustomerQueueEntryEntity();
            entry.setCustomer(customer);
            entry.setQueue(updatedQueue);
            entry.setQueueNumber(queueNumber);
            entry.setDisplayName(displayName == null || displayName.isBlank() ? updatedQueue.getServiceName() : displayName.trim());
            savedEntry = customerQueueEntryRepository.save(entry);
        } else {
            GuestQueueEntryEntity guestEntry = new GuestQueueEntryEntity();
            guestEntry.setQueue(updatedQueue);
            guestEntry.setQueueNumber(queueNumber);
            guestEntry.setFirstName(normalizeRequired(guestFirstName, "Qeydiyyatsiz musteri ucun ad mutleqdir."));
            guestEntry.setLastName(normalizeRequired(guestLastName, "Qeydiyyatsiz musteri ucun soyad mutleqdir."));
            guestQueueEntryRepository.save(guestEntry);
        }

        return new QueueJoinResult(updatedQueue, savedEntry, queueNumber, waitingCount, estimatedWaitMinutes);
    }

    private QueueEntity ensureQueueState(QueueEntity queue) {
        if (!queue.isActive() || queue.getRegistration().getStatus() == RegistrationStatus.EXPIRED) {
            if (queue.isActive() && queue.getRegistration().getStatus() == RegistrationStatus.EXPIRED) {
                queue.setActive(false);
                queueRepository.save(queue);
            }
            return queue;
        }
        if (queue.getResetMode() == QueueResetMode.MANUAL || queue.getResetAt() == null) {
            return queue;
        }
        if (!LocalDateTime.now().isBefore(queue.getResetAt())) {
            queue.setActive(false);
            queue.setCurrentServingNumber(0);
            queue.setLastIssuedNumber(0);
            queue.setLastAdvancedAt(null);
            return queueRepository.save(queue);
        }
        return queue;
    }

    private void expireRegistration(RegistrationEntity registration) {
        registration.setStatus(RegistrationStatus.EXPIRED);
        registration.setPaid(false);
        registrationRepository.save(registration);

        queueRepository.findByRegistrationIdOrderByIdAsc(registration.getId())
                .forEach(queue -> {
                    queue.setActive(false);
                    queueRepository.save(queue);
                });
    }

    private QueueResetMode resolveResetMode(String value) {
        if (value == null || value.isBlank()) {
            return QueueResetMode.DAILY;
        }
        try {
            return QueueResetMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset novu duzgun deyil.");
        }
    }

    private LocalDateTime resolveResetAt(QueueResetMode resetMode, String value) {
        if (resetMode == QueueResetMode.MANUAL) {
            return null;
        }
        if (resetMode == QueueResetMode.DAILY) {
            return LocalDate.now().plusDays(1).atStartOfDay();
        }
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Secilen tarix mutleqdir.");
        }
        try {
            return LocalDate.parse(value.trim()).plusDays(1).atStartOfDay();
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tarix YYYY-MM-DD formatinda olmalidir.");
        }
    }

    private void validateCustomerQueueEntryAccess(CustomerQueueEntryEntity entry, Long customerId) {
        if (customerId == null || !Objects.equals(entry.getCustomer().getId(), customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu musteri novbe kaydina icazeniz yoxdur.");
        }
    }

    private void validateQueueAccess(QueueEntity queue, Long registrationId, Long queueManagerId) {
        boolean ownerAccess = registrationId != null && queue.getRegistration().getId().equals(registrationId);
        boolean managerAccess = false;

        if (queueManagerId != null) {
            QueueManagerEntity manager = queueManagerRepository.findById(queueManagerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Queue user tapilmadi."));
            managerAccess = manager.getQueue().getId().equals(queue.getId());
        }

        if (!ownerAccess && !managerAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu novbeni idare etmek icazeniz yoxdur.");
        }
    }

    private String buildScanMessage(long queueNumber, long currentServingNumber, long waitingCount, long estimatedWaitMinutes) {
        return "Hal hazirda novbeniz " + queueNumber + "-dir. Hal hazirda "
                + currentServingNumber + " nomreli musteriye xidmet olunur. "
                + waitingCount + " nefer gozlemededir. "
                + "Ortalama size novbeniz " + formatDuration(estimatedWaitMinutes) + " hesablanir.";
    }

    private String formatDuration(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours <= 0) {
            return minutes + " deq";
        }
        return hours + " saat " + minutes + " deq";
    }

    private boolean matchesSearch(AdminRegistrationItemResponse item, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }

        String normalized = search.trim().toLowerCase();
        return item.fullName().toLowerCase().contains(normalized)
                || item.email().toLowerCase().contains(normalized)
                || (item.paymentReference() != null && item.paymentReference().toLowerCase().contains(normalized));
    }

    private boolean matchesRegistrationType(AdminRegistrationItemResponse item, String registrationType) {
        if (registrationType == null || registrationType.isBlank() || "ALL".equalsIgnoreCase(registrationType)) {
            return true;
        }

        return item.registrationType().name().equalsIgnoreCase(registrationType.trim());
    }

    private boolean matchesPaymentStatus(AdminRegistrationItemResponse item, String paymentStatus) {
        if (paymentStatus == null || paymentStatus.isBlank() || "ALL".equalsIgnoreCase(paymentStatus)) {
            return true;
        }

        if ("PAID".equalsIgnoreCase(paymentStatus)) {
            return item.paid();
        }
        if ("UNPAID".equalsIgnoreCase(paymentStatus)) {
            return !item.paid();
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment filter duzgun deyil.");
    }

    private boolean matchesMonth(AdminRegistrationItemResponse item, String month) {
        if (month == null || month.isBlank()) {
            return true;
        }

        try {
            return YearMonth.from(item.createdAt()).equals(YearMonth.parse(month.trim()));
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month filter YYYY-MM formatinda olmalidir.");
        }
    }

    private record QueueJoinResult(
            QueueEntity queue,
            CustomerQueueEntryEntity entry,
            long queueNumber,
            long waitingCount,
            long estimatedWaitMinutes
    ) {
    }
}
