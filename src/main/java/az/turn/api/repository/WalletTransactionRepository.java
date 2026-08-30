package az.turn.api;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface WalletTransactionRepository extends Repository<WalletTransactionEntity, Long> {
    WalletTransactionEntity save(WalletTransactionEntity transaction);

    Optional<WalletTransactionEntity> findById(long id);

    Optional<WalletTransactionEntity> findByWalletAccountIdAndReferenceKey(long walletAccountId, String referenceKey);

    Slice<WalletTransactionEntity> findByWalletAccountUserIdOrderByCreatedAtDescIdDesc(
            long userId,
            Pageable pageable
    );

    long countByWalletAccountId(long walletAccountId);
}
