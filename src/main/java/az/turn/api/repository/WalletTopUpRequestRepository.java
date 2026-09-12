package az.turn.api;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Collection;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    Slice<WalletTopUpRequestEntity> findByStatusInOrderByCreatedAtDescIdDesc(
            Collection<WalletTopUpRequestStatus> statuses,
            Pageable pageable
    );

    long countByStatusIn(Collection<WalletTopUpRequestStatus> statuses);

    @Query("select coalesce(sum(request.amountAzn), 0) from WalletTopUpRequestEntity request "
            + "where request.status = :status and request.receiptUploadedAt >= :from and request.receiptUploadedAt < :to")
    BigDecimal sumAmountByStatusAndReceiptUploadedAt(
            WalletTopUpRequestStatus status,
            LocalDateTime from,
            LocalDateTime to
    );

    @Query("select coalesce(sum(request.amountAzn), 0) from WalletTopUpRequestEntity request "
            + "where request.status in :statuses and request.reviewedAt >= :from and request.reviewedAt < :to")
    BigDecimal sumAmountByStatusInAndReviewedAt(
            Collection<WalletTopUpRequestStatus> statuses,
            LocalDateTime from,
            LocalDateTime to
    );

    Slice<WalletTopUpRequestEntity> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from WalletTopUpRequestEntity request where request.id = :requestId")
    Optional<WalletTopUpRequestEntity> findByIdForUpdate(long requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from WalletTopUpRequestEntity request where request.activeUserId = :userId")
    Optional<WalletTopUpRequestEntity> findActiveByUserIdForUpdate(long userId);
}
