package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AdminAccountService {
    private final AdminAccountRepository repository;
    private final AdminManagementMapper mapper;
    private final UserPasswordService userPasswordService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAuditService auditService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    public AdminAccountService(
            AdminAccountRepository repository,
            AdminManagementMapper mapper,
            UserPasswordService userPasswordService,
            PasswordEncoder passwordEncoder,
            PlatformAuditService auditService,
            RefreshTokenRepository refreshTokenRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.userPasswordService = userPasswordService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public void bootstrap(String username, String passwordHash, boolean mustChangeCredentials) {
        String normalizedUsername = normalizeUsername(username);
        if (passwordHash == null || !passwordHash.matches("^\\$2[aby]\\$.+")) {
            throw new IllegalStateException("Default admin şifrəsi BCrypt hash olmalıdır.");
        }
        AdminAccountEntity existing = repository.findByUsernameForUpdate(normalizedUsername).orElse(null);
        if (existing != null) {
            if (mustChangeCredentials && existing.isMustChangeCredentials()) {
                existing.setPasswordHash(passwordHash);
            }
            return;
        }
        AdminAccountEntity admin = new AdminAccountEntity();
        admin.setUsername(normalizedUsername);
        admin.setDisplayName("Baş administrator");
        admin.setPasswordHash(passwordHash);
        admin.setActive(true);
        admin.setMustChangeCredentials(mustChangeCredentials);
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
                admin.isMustChangeCredentials(),
                null
        );
    }

    @Transactional
    public AdminLoginResponse changeRequiredCredentials(
            String actorUsername,
            AdminCredentialChangeRequestDto request
    ) {
        String currentUsername = normalizeUsername(actorUsername);
        AdminAccountEntity admin = repository.findByUsernameForUpdate(currentUsername)
                .orElseThrow(this::invalidCredentials);
        if (!admin.isActive() || !matches(request.currentPassword(), admin.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!admin.isMustChangeCredentials()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "İlkin giriş məlumatları artıq dəyişdirilib.");
        }

        String newUsername = normalizeUsername(request.newUsername());
        if (newUsername.equals(currentUsername)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yeni istifadəçi adı ilkin istifadəçi adından fərqli olmalıdır.");
        }
        if (repository.existsByUsername(newUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu admin istifadəçi adı artıq mövcuddur.");
        }
        if (matches(request.newPassword(), admin.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Yeni şifrə ilkin şifrədən fərqli olmalıdır.");
        }

        admin.setUsername(newUsername);
        admin.setPasswordHash(userPasswordService.encode(request.newPassword()));
        admin.setMustChangeCredentials(false);
        admin.setCredentialsChangedAt(LocalDateTime.now(clock));
        repository.saveAndFlush(admin);
        refreshTokenRepository.revokeAllForUsername(
                AuthUserType.ADMIN,
                currentUsername,
                LocalDateTime.now(clock),
                SessionRevocationReason.CREDENTIALS_CHANGED
        );
        auditService.record(
                "ADMIN",
                currentUsername,
                "ADMIN_CREDENTIALS_CHANGED",
                "ADMIN_ACCOUNT",
                admin.getId(),
                "username=" + currentUsername + "->" + newUsername
        );
        return new AdminLoginResponse(
                newUsername,
                "ADMIN",
                "Admin giriş məlumatları yeniləndi.",
                false,
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
        admin.setMustChangeCredentials(false);
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
