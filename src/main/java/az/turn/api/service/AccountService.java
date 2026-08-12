package az.turn.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class AccountService {

    private final RegistrationRepository registrationRepository;
    private final CustomerRepository customerRepository;
    private final QueueManagerRepository queueManagerRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPasswordHash;

    public AccountService(
            RegistrationRepository registrationRepository,
            CustomerRepository customerRepository,
            QueueManagerRepository queueManagerRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username:admin}") String adminUsername,
            @Value("${app.admin.password-hash}") String adminPasswordHash
    ) {
        this.registrationRepository = registrationRepository;
        this.customerRepository = customerRepository;
        this.queueManagerRepository = queueManagerRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPasswordHash = adminPasswordHash;
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

    @Transactional(readOnly = true)
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
                toQueueDetailResponse(manager.getQueue()),
                null
        );
    }

    @Transactional(readOnly = true)
    public AdminLoginResponse loginAdmin(AdminLoginRequest request) {
        String username = normalizeRequired(request.username(), "Admin istifadeci adi mutleqdir.");
        String password = normalizeRequired(request.password(), "Admin sifresi mutleqdir.");

        if (!adminUsername.equals(username) || !passwordEncoder.matches(password, adminPasswordHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin username ve ya password sehvdir.");
        }

        return new AdminLoginResponse(adminUsername, "ADMIN", "Admin panelinÉ™ uÄŸurla daxil oldunuz.", null);
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

    private QueueDetailResponse toQueueDetailResponse(QueueEntity queue) {
        long averageServiceMinutes = Math.max(1, queue.getAverageServiceMinutes());
        long waitingCount = Math.max(0, queue.getLastIssuedNumber() - queue.getCurrentServingNumber());
        long estimatedWaitMinutes = waitingCount * averageServiceMinutes;
        return new QueueDetailResponse(
                queue.getId(),
                queue.getRegistration().getId(),
                queue.getRegistration().getRegistrationType(),
                queue.getRegistration().getFullName(),
                queue.getRegistration().getEmail(),
                queue.getAddress(),
                queue.getServiceName(),
                queue.getCategories() == null ? java.util.List.of() : java.util.List.copyOf(queue.getCategories()),
                queue.getQrToken(),
                queue.getCurrentServingNumber(),
                queue.getLastIssuedNumber(),
                waitingCount,
                queue.getLastIssuedNumber(),
                averageServiceMinutes,
                estimatedWaitMinutes,
                queue.getLastAdvancedAt(),
                null,
                queue.getResetMode(),
                queue.getResetAt(),
                queue.isActive()
        );
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
