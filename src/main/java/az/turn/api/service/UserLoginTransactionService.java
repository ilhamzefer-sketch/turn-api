package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class UserLoginTransactionService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final UserPasswordService userPasswordService;
    private final Clock clock;

    public UserLoginTransactionService(
            UserRepository userRepository,
            UserPasswordService userPasswordService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.userPasswordService = userPasswordService;
        this.clock = clock;
    }

    @Transactional
    public UserLoginOutcome authenticate(String normalizedPhone, String password) {
        UserEntity user = userRepository.findByNormalizedPhoneForUpdate(normalizedPhone).orElse(null);
        if (user == null) {
            return UserLoginOutcome.failure(null, UserLoginFailure.INVALID_CREDENTIALS);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            return UserLoginOutcome.failure(user, UserLoginFailure.LOCKED);
        }
        if (user.getLockedUntil() != null) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }

        UserLoginFailure statusFailure = resolveStatusFailure(user.getStatus());
        if (statusFailure != UserLoginFailure.NONE) {
            return UserLoginOutcome.failure(user, statusFailure);
        }

        if (!userPasswordService.matches(password, user.getPasswordHash())) {
            int failedAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failedAttempts);
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
                return UserLoginOutcome.failure(user, UserLoginFailure.LOCKED);
            }
            return UserLoginOutcome.failure(user, UserLoginFailure.INVALID_CREDENTIALS);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        return UserLoginOutcome.success(user);
    }

    private UserLoginFailure resolveStatusFailure(UserStatus status) {
        return switch (status) {
            case ACTIVE -> UserLoginFailure.NONE;
            case PENDING -> UserLoginFailure.REGISTRATION_REQUIRED;
            case PASSWORD_RESET_REQUIRED -> UserLoginFailure.PASSWORD_RESET_REQUIRED;
            case SUSPENDED -> UserLoginFailure.SUSPENDED;
            case ANONYMIZED -> UserLoginFailure.INVALID_CREDENTIALS;
        };
    }
}
