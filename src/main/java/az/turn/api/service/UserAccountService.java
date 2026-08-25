package az.turn.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final GuestQueueEntryRepository guestQueueEntryRepository;
    private final GuestContactRepository guestContactRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PhoneNumberService phoneNumberService;
    private final UserPasswordService userPasswordService;
    private final UserLoginTransactionService userLoginTransactionService;
    private final Clock clock;

    public UserAccountService(
            UserRepository userRepository,
            GuestQueueEntryRepository guestQueueEntryRepository,
            GuestContactRepository guestContactRepository,
            RefreshTokenRepository refreshTokenRepository,
            PhoneNumberService phoneNumberService,
            UserPasswordService userPasswordService,
            UserLoginTransactionService userLoginTransactionService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.guestQueueEntryRepository = guestQueueEntryRepository;
        this.guestContactRepository = guestContactRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.phoneNumberService = phoneNumberService;
        this.userPasswordService = userPasswordService;
        this.userLoginTransactionService = userLoginTransactionService;
        this.clock = clock;
    }

    @Transactional
    public UserEntity register(UserRegistrationRequestDto request) {
        String normalizedPhone = phoneNumberService.normalizeAzerbaijaniPhone(request.phone());
        String passwordHash = userPasswordService.encode(request.password());
        UserEntity existing = userRepository.findByNormalizedPhoneForUpdate(normalizedPhone).orElse(null);
        UserEntity user = existing == null
                ? createActiveUser(request, normalizedPhone, passwordHash)
                : completeExistingAccount(existing, request, passwordHash);

        try {
            UserEntity savedUser = userRepository.saveAndFlush(user);
            guestQueueEntryRepository.linkUnclaimedEntries(savedUser, normalizedPhone);
            linkGuestContact(savedUser, normalizedPhone);
            return savedUser;
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu telefon nömrəsi artıq qeydiyyatdan keçib.");
        }
    }

    private void linkGuestContact(UserEntity user, String normalizedPhone) {
        GuestContactEntity contact = guestContactRepository.findByNormalizedPhoneForUpdate(normalizedPhone).orElse(null);
        if (contact != null && contact.getLinkedUser() == null) {
            contact.setLinkedUser(user);
            contact.setLinkedAt(LocalDateTime.now(clock));
            guestContactRepository.save(contact);
        }
    }

    public UserEntity login(UserLoginRequestDto request) {
        String normalizedPhone = phoneNumberService.normalizeAzerbaijaniPhone(request.phone());
        UserLoginOutcome outcome = userLoginTransactionService.authenticate(normalizedPhone, request.password());
        return switch (outcome.failure()) {
            case NONE -> outcome.user();
            case INVALID_CREDENTIALS -> throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Telefon nömrəsi və ya şifrə səhvdir.");
            case LOCKED -> throw new ResponseStatusException(HttpStatus.LOCKED, "Hesab 15 dəqiqəlik kilidlənib. Daha sonra yenidən cəhd edin.");
            case REGISTRATION_REQUIRED -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu nömrə üçün gözləyən hesab var. Qeydiyyatı tamamlayın.");
            case PASSWORD_RESET_REQUIRED -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Yeni şifrə təyin etmək üçün qeydiyyat formasından istifadə edin.");
            case SUSPENDED -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hesab dayandırılıb.");
        };
    }

    private UserEntity createActiveUser(
            UserRegistrationRequestDto request,
            String normalizedPhone,
            String passwordHash
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        UserEntity user = new UserEntity();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setNormalizedPhone(normalizedPhone);
        user.setPasswordHash(passwordHash);
        user.setStatus(UserStatus.ACTIVE);
        user.setActivatedAt(now);
        user.setPasswordChangedAt(now);
        return user;
    }

    private UserEntity completeExistingAccount(
            UserEntity user,
            UserRegistrationRequestDto request,
            String passwordHash
    ) {
        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu telefon nömrəsi artıq qeydiyyatdan keçib.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hesab dayandırılıb.");
        }
        if (user.getStatus() == UserStatus.ANONYMIZED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu telefon nömrəsi ilə qeydiyyat tamamlana bilmir.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (user.getStatus() == UserStatus.PENDING) {
            user.setFirstName(request.firstName().trim());
            user.setLastName(request.lastName().trim());
        } else {
            refreshTokenRepository.revokeAllForUser(
                    AuthUserType.USER,
                    user.getId(),
                    LocalDateTime.now(clock),
                    SessionRevocationReason.CREDENTIALS_CHANGED
            );
        }
        user.setPasswordHash(passwordHash);
        user.setStatus(UserStatus.ACTIVE);
        user.setActivatedAt(user.getActivatedAt() == null ? now : user.getActivatedAt());
        user.setPasswordChangedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return user;
    }
}
