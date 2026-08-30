package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface WalletAccountRepository extends JpaRepository<WalletAccountEntity, Long> {
    Optional<WalletAccountEntity> findByUserId(long userId);

    boolean existsByUserId(long userId);

    @Query("select wallet.user.id as userId, wallet.balance as balance from WalletAccountEntity wallet "
            + "where wallet.user.id in :userIds")
    List<WalletUserBalanceProjection> findBalancesByUserIds(Collection<Long> userIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from WalletAccountEntity wallet where wallet.user.id = :userId")
    Optional<WalletAccountEntity> findByUserIdForUpdate(long userId);
}
