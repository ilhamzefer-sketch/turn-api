package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class AdminAccountService {
    private final AdminAccountRepository repository;
    private final AdminManagementMapper mapper;
    private final UserPasswordService userPasswordService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService auditService;

    public AdminAccountService(
            AdminAccountRepository repository,
            AdminManagementMapper mapper,
            UserPasswordService userPasswordService,
            PasswordEncoder passwordEncoder,
            PlatformAuditService auditService
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.userPasswordService = userPasswordService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public void bootstrap(String username, String passwordHash) {
        String normalizedUsername = normalizeUsername(username);
        if (repository.existsByUsername(normalizedUsername)) return;
        if (passwordHash == null || !passwordHash.matches("^\\$2[aby]\\$.+")) {
            throw new IllegalStateException("Default admin şifrəsi BCrypt hash olmalıdır.");
        }
        AdminAccountEntity admin = new AdminAccountEntity();
        admin.setUsername(normalizedUsername);
        admin.setDisplayName("Baş administrator");
        admin.setPasswordHash(passwordHash);
        admin.setActive(true);
        repository.saveAndFlush(admin);
    }

    @Transactional(readOnly = true)
    public boolean exists(String username) {
        return repository.existsByUsername(normalizeUsername(username));
    }

    @Transactional(readOnly = true)
    public AdminLoginResponse login(AdminLoginRequest request) {
        String username = normalizeUsername(request.username());
        AdminAccountEntity admin = repository.findByUsername(username)
                .orElseThrow(this::invalidCredentials);
        if (!admin.isActive() || !matches(request.password(), admin.getPasswordHash())) {
            throw invalidCredentials();
        }
        return new AdminLoginResponse(
                admin.getUsername(),
                "ADMIN",
                "Admin panelinə uğurla daxil oldunuz.",
                null
        );
    }

    @Transactional(readOnly = true)
    public List<AdminAccountDto> list() {
        return repository.findAllByOrderByCreatedAtAsc().stream().map(mapper::toAdminAccountDto).toList();
    }

    @Transactional
    public AdminAccountDto create(String actorUsername, AdminAccountCreateRequestDto request) {
        String username = normalizeUsername(request.username());
        if (repository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu admin istifadəçi adı artıq mövcuddur.");
        }
        AdminAccountEntity admin = new AdminAccountEntity();
        admin.setUsername(username);
        admin.setDisplayName(required(request.displayName(), "Admin adı mütləqdir."));
        admin.setPasswordHash(userPasswordService.encode(request.password()));
        admin.setActive(true);
        admin.setCreatedByUsername(actorUsername);
        AdminAccountEntity saved = repository.save(admin);
        auditService.record(
                "ADMIN",
                actorUsername,
                "ADMIN_ACCOUNT_CREATED",
                "ADMIN_ACCOUNT",
                saved.getId(),
                "username=" + saved.getUsername()
        );
        return mapper.toAdminAccountDto(saved);
    }

    private boolean matches(String password, String passwordHash) {
        return passwordHash.startsWith("{bcrypt-sha256}")
                ? userPasswordService.matches(password, passwordHash)
                : passwordEncoder.matches(password, passwordHash);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin istifadəçi adı və ya şifrə səhvdir.");
    }

    private String normalizeUsername(String value) {
        String normalized = required(value, "Admin istifadəçi adı mütləqdir.").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9._-]{3,50}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin istifadəçi adı düzgün formatda deyil.");
        }
        return normalized;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return value.trim();
    }
}
