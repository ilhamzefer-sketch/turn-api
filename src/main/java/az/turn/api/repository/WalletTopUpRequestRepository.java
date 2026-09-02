package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Collection;

public interface WalletTopUpRequestRepository extends JpaRepository<WalletTopUpRequestEntity, Long> {
    Optional<WalletTopUpRequestEntity> findByActiveUserId(long userId);

    boolean existsByActiveUserId(long userId);

    Slice<WalletTopUpRequestEntity> findByUserIdOrderByCreatedAtDescIdDesc(long userId, Pageable pageable);

    Slice<WalletTopUpRequestEntity> findByStatusOrderByReceiptUploadedAtAscIdAsc(
            WalletTopUpRequestStatus status,
            Pageable pageable
    );

    Slice<WalletTopUpRequestEntity> findByStatusInOrderByReceiptUploadedAtAscIdAsc(
            Collection<WalletTopUpRequestStatus> statuses,
            Pageable pageable
    );

    Slice<WalletTopUpRequestEntity> findAllByOrderByCreatedAtAscIdAsc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from WalletTopUpRequestEntity request where request.id = :requestId")
    Optional<WalletTopUpRequestEntity> findByIdForUpdate(long requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from WalletTopUpRequestEntity request where request.activeUserId = :userId")
    Optional<WalletTopUpRequestEntity> findActiveByUserIdForUpdate(long userId);
}
