package az.turn.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class WalletTransactionService {
    private final WalletAccountRepository walletAccountRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final WalletMapper walletMapper;
    private final Clock clock;

    public WalletTransactionService(
            WalletAccountRepository walletAccountRepository,
            WalletTransactionRepository walletTransactionRepository,
            UserRepository userRepository,
            WalletMapper walletMapper,
            Clock clock
    ) {
        this.walletAccountRepository = walletAccountRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.userRepository = userRepository;
        this.walletMapper = walletMapper;
        this.clock = clock;
    }

    @Transactional
    public WalletTransactionDto apply(long userId, WalletTransactionCommandDto suppliedCommand) {
        WalletTransactionCommandDto command = validateAndNormalize(userId, suppliedCommand);
        WalletAccountEntity wallet = walletAccountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("İstifadəçinin balans hesabı yaradılmayıb."));
        WalletTransactionEntity replay = walletTransactionRepository
                .findByWalletAccountIdAndReferenceKey(wallet.getId(), command.referenceKey())
                .orElse(null);
        if (replay != null) {
            requireMatchingReplay(replay, command);
            return walletMapper.toTransactionDto(replay);
        }

        UserEntity actorUser = resolveActorUser(userId, command);
        long balanceBefore = wallet.getBalance();
        requireSufficientBalance(balanceBefore, command);
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            wallet.apply(command.type().direction(), command.amount(), now);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coin məbləği qəbul edilən həddi keçir.");
        }
        walletAccountRepository.save(wallet);
        WalletTransactionEntity transaction = new WalletTransactionEntity(
                wallet,
                command.type(),
                command.amount(),
                balanceBefore,
                wallet.getBalance(),
                command.actorType(),
                actorUser,
                command.actorReference(),
                command.referenceKey(),
                command.description(),
                now
        );
        return walletMapper.toTransactionDto(walletTransactionRepository.save(transaction));
    }

    private WalletTransactionCommandDto validateAndNormalize(
            long userId,
            WalletTransactionCommandDto command
    ) {
        if (command == null || command.type() == null || command.actorType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coin əməliyyatının növü və icraçısı mütləqdir.");
        }
        if (command.amount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coin məbləği sıfırdan böyük olmalıdır.");
        }
        String referenceKey = required(command.referenceKey(), 180, "Coin əməliyyatı üçün unikal istinad mütləqdir.");
        String actorReference = optional(command.actorReference(), 160, "İcraçı istinadı 160 simvoldan uzun ola bilməz.");
        String description = optional(command.description(), 1000, "Əməliyyat izahı 1000 simvoldan uzun ola bilməz.");
        validateActor(userId, command.actorType(), command.actorUserId(), actorReference);
        if (command.type() == WalletTransactionType.ADMIN_CREDIT
                && command.actorType() != WalletActorType.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin coin əlavəsini yalnız admin icra edə bilər.");
        }
        if (command.type() == WalletTransactionType.ADMIN_CREDIT && description == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin coin əlavəsi üçün səbəb mütləqdir.");
        }
        return new WalletTransactionCommandDto(
                command.type(),
                command.amount(),
                command.actorType(),
                command.actorUserId(),
                actorReference,
                referenceKey,
                description
        );
    }

    private void validateActor(
            long userId,
            WalletActorType actorType,
            Long actorUserId,
            String actorReference
    ) {
        if (actorType == WalletActorType.USER && !Objects.equals(actorUserId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "İstifadəçi yalnız öz balans əməliyyatını başlada bilər.");
        }
        if (actorType != WalletActorType.USER && actorReference == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin və sistem əməliyyatlarında icraçı istinadı mütləqdir.");
        }
        if (actorType != WalletActorType.USER && actorUserId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Admin və sistem əməliyyatlarında user icraçısı göstərilə bilməz.");
        }
    }

    private UserEntity resolveActorUser(long userId, WalletTransactionCommandDto command) {
        if (command.actorType() != WalletActorType.USER) {
            return null;
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "İstifadəçi tapılmadı."));
    }

    private void requireSufficientBalance(long balance, WalletTransactionCommandDto command) {
        if (command.type().direction() == WalletTransactionDirection.DEBIT && balance < command.amount()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Balansda kifayət qədər coin yoxdur.");
        }
    }

    private void requireMatchingReplay(
            WalletTransactionEntity existing,
            WalletTransactionCommandDto command
    ) {
        Long existingActorUserId = existing.getActorUser() == null ? null : existing.getActorUser().getId();
        boolean matches = existing.getType() == command.type()
                && existing.getAmount() == command.amount()
                && existing.getActorType() == command.actorType()
                && Objects.equals(existingActorUserId, command.actorUserId())
                && Objects.equals(existing.getActorReference(), command.actorReference())
                && Objects.equals(existing.getDescription(), command.description());
        if (!matches) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu istinad başqa coin əməliyyatı üçün istifadə olunub.");
        }
    }

    private String required(String value, int maxLength, String message) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String optional(String value, int maxLength, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }
}
