package az.turn.api;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class SessionPrincipalService {
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private final CustomerRepository customerRepository;
    private final QueueManagerRepository queueManagerRepository;
    private final AdminAccountRepository adminAccountRepository;

    public SessionPrincipalService(
            UserRepository userRepository,
            RegistrationRepository registrationRepository,
            CustomerRepository customerRepository,
            QueueManagerRepository queueManagerRepository,
            AdminAccountRepository adminAccountRepository
    ) {
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
        this.customerRepository = customerRepository;
        this.queueManagerRepository = queueManagerRepository;
        this.adminAccountRepository = adminAccountRepository;
    }

    public PrincipalState resolve(AuthenticatedUser principal) {
        return switch (principal.userType()) {
            case USER -> resolveUser(principal.userId());
            case REGISTRATION -> resolveRegistration(principal.userId());
            case CUSTOMER -> resolveCustomer(principal.userId());
            case QUEUE_MANAGER -> resolveQueueManager(principal.userId());
            case ADMIN -> resolveAdmin(principal.username());
        };
    }

    private PrincipalState resolveUser(Long id) {
        return id == null ? inactive() : userRepository.findById(id)
                .map(user -> new PrincipalState(
                        user.getStatus() == UserStatus.ACTIVE,
                        version(AuthUserType.USER, id, user.getPasswordHash())
                ))
                .orElseGet(this::inactive);
    }

    private PrincipalState resolveRegistration(Long id) {
        return id == null ? inactive() : registrationRepository.findById(id)
                .map(registration -> new PrincipalState(
                        registration.getStatus() == RegistrationStatus.ACTIVE && registration.isPaid(),
                        version(AuthUserType.REGISTRATION, id, registration.getPasswordHash())
                ))
                .orElseGet(this::inactive);
    }

    private PrincipalState resolveCustomer(Long id) {
        return id == null ? inactive() : customerRepository.findById(id)
                .map(customer -> new PrincipalState(
                        true,
                        version(AuthUserType.CUSTOMER, id, customer.getPasswordHash())
                ))
                .orElseGet(this::inactive);
    }

    private PrincipalState resolveQueueManager(Long id) {
        return id == null ? inactive() : queueManagerRepository.findById(id)
                .map(manager -> new PrincipalState(
                        true,
                        version(AuthUserType.QUEUE_MANAGER, id, manager.getPasswordHash())
                ))
                .orElseGet(this::inactive);
    }

    private PrincipalState resolveAdmin(String username) {
        if (username == null) return inactive();
        return adminAccountRepository.findByUsername(username)
                .map(admin -> new PrincipalState(
                        admin.isActive(),
                        version(AuthUserType.ADMIN, admin.getId(), admin.getPasswordHash())
                ))
                .orElseGet(this::inactive);
    }

    private PrincipalState inactive() {
        return new PrincipalState(false, null);
    }

    private String version(AuthUserType userType, Long id, String credentialHash) {
        String source = userType.name() + ":" + id + ":" + (credentialHash == null ? "" : credentialHash);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 mövcud deyil.", exception);
        }
    }
}
