package az.turn.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentSessionService {
    private static final long FERDI_PAYMENT_AMOUNT = 20;
    private static final long KORPORATIV_PAYMENT_AMOUNT = 100;

    private final RegistrationRepository registrationRepository;
    private final PaymentSessionRepository paymentSessionRepository;
    private final Map<String, PaymentProvider> paymentProviders;
    private final PasswordEncoder passwordEncoder;
    private final String paymentMode;
    private final String paymentProviderName;

    public PaymentSessionService(
            RegistrationRepository registrationRepository,
            PaymentSessionRepository paymentSessionRepository,
            List<PaymentProvider> paymentProviders,
            PasswordEncoder passwordEncoder,
            @Value("${app.payment.mode:sandbox}") String paymentMode,
            @Value("${app.payment.provider:mock}") String paymentProviderName
    ) {
        this.registrationRepository = registrationRepository;
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
        PaymentProvider paymentProvider = resolvePaymentProvider(savedSession.getProvider());
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

    @Transactional
    public PaymentSessionResponse cancelRegistrationPayment(long paymentSessionId, String sessionToken) {
        PaymentSessionEntity session = paymentSessionRepository.findByIdForUpdate(paymentSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Odenis sessiyasi tapilmadi."));
        validatePaymentSessionToken(session, sessionToken);
        if ("birbank".equalsIgnoreCase(session.getProvider())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bank odenisi tetbiqden legv edilmir. Bank sehifesinde imtina edin.");
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

    private PaymentConfirmationResponse processPaymentConfirmation(long paymentSessionId, String sessionToken, boolean issueAuthentication) {
        PaymentSessionEntity session = paymentSessionRepository.findByIdForUpdate(paymentSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Odenis sessiyasi tapilmadi."));
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

        PaymentProvider paymentProvider = resolvePaymentProvider(session.getProvider());
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
        return new PaymentConfirmationResponse(
                toPaymentSessionResponse(savedSession),
                issueAuthentication ? toRegistrationResponse(savedRegistration) : null
        );
    }

    private PaymentProvider resolvePaymentProvider(String providerName) {
        PaymentProvider paymentProvider = paymentProviders.get(providerName.toLowerCase());
        if (paymentProvider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment provider tapilmadi.");
        }
        return paymentProvider;
    }

    private PaymentSessionEntity findPaymentSession(long paymentSessionId) {
        return paymentSessionRepository.findById(paymentSessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Odenis sessiyasi tapilmadi."));
    }

    private void validatePaymentSessionToken(PaymentSessionEntity session, String sessionToken) {
        String storedToken = session.getSessionToken();
        String comparableToken = storedToken != null && storedToken.startsWith("sha256:")
                ? hashPaymentSessionToken(sessionToken == null ? "" : sessionToken)
                : sessionToken;
        byte[] expected = storedToken == null ? new byte[0] : storedToken.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = comparableToken == null ? new byte[0] : comparableToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Odenis sessiyasi tapilmadi.");
        }
    }

    private RegistrationEntity findSessionRegistration(PaymentSessionEntity session) {
        if (session.getRegistration() == null || session.getRegistration().getId() == null) {
            return null;
        }
        return registrationRepository.findById(session.getRegistration().getId()).orElse(null);
    }

    private RegistrationEntity getOrCreateSessionRegistration(PaymentSessionEntity session) {
        RegistrationEntity registration = findSessionRegistration(session);
        if (registration == null) {
            registration = new RegistrationEntity();
            registration.setCreatedAt(LocalDateTime.now());
        }
        registration.setFirstName(session.getFirstName());
        registration.setLastName(session.getLastName());
        registration.setEmail(session.getEmail());
        registration.setPasswordHash(session.getPasswordHash());
        registration.setRegistrationType(session.getRegistrationType());
        return registration;
    }

    private void expireRegistration(RegistrationEntity registration) {
        registration.setStatus(RegistrationStatus.EXPIRED);
        registrationRepository.save(registration);
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

    private long resolvePaymentAmount(RegistrationType registrationType) {
        return registrationType == RegistrationType.KORPORATIV ? KORPORATIV_PAYMENT_AMOUNT : FERDI_PAYMENT_AMOUNT;
    }

    private String resolveCardLast4(String cardNumber) {
        return cardNumber.length() >= 4 ? cardNumber.substring(cardNumber.length() - 4) : cardNumber;
    }

    private String resolveSandboxOutcome(String cardNumber) {
        if (cardNumber.endsWith("0002")) {
            return "decline";
        }
        if (cardNumber.endsWith("0003")) {
            return "pending";
        }
        return "approve";
    }

    private String normalizeCardHolder(String cardHolder, String firstName, String lastName) {
        if (cardHolder == null || cardHolder.trim().isEmpty()) {
            return firstName + " " + lastName;
        }
        return cardHolder.trim();
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String hashPaymentSessionToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
