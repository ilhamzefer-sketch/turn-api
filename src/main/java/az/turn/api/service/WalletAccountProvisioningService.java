package az.turn.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class WalletAccountProvisioningService {
    private final WalletAccountRepository walletAccountRepository;
    private final Clock clock;

    public WalletAccountProvisioningService(WalletAccountRepository walletAccountRepository, Clock clock) {
        this.walletAccountRepository = walletAccountRepository;
        this.clock = clock;
    }

    @Transactional
    public WalletAccountEntity provision(UserEntity user) {
        return walletAccountRepository.findByUserId(user.getId())
                .orElseGet(() -> walletAccountRepository.save(
                        new WalletAccountEntity(user, LocalDateTime.now(clock))
                ));
    }
}
