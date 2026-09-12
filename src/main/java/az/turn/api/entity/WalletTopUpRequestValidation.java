package az.turn.api;

import java.util.Objects;

final class WalletTopUpRequestValidation {
    private WalletTopUpRequestValidation() {}

    static void requireTopUpTransaction(WalletTransactionEntity transaction, Long userId, long coinAmount) {
        WalletTransactionEntity suppliedTransaction = Objects.requireNonNull(transaction);
        Long walletUserId = suppliedTransaction.getWalletAccount().getUser().getId();
        if (suppliedTransaction.getType() != WalletTransactionType.TOP_UP
                || walletUserId == null
                || !walletUserId.equals(userId)
                || suppliedTransaction.getAmount() != coinAmount) {
            throw new IllegalArgumentException("Coin əməliyyatı balans artırma sorğusuna uyğun deyil.");
        }
    }

    static void requireReversalTransaction(WalletTransactionEntity transaction, Long userId, long coinAmount) {
        WalletTransactionEntity suppliedTransaction = Objects.requireNonNull(transaction);
        Long walletUserId = suppliedTransaction.getWalletAccount().getUser().getId();
        if (suppliedTransaction.getType() != WalletTransactionType.TOP_UP_REVERSAL
                || walletUserId == null
                || !walletUserId.equals(userId)
                || suppliedTransaction.getAmount() != coinAmount) {
            throw new IllegalArgumentException("Coin geri çəkmə əməliyyatı sorğuya uyğun deyil.");
        }
    }

    static String requireNonBlank(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    static void requireStatus(WalletTopUpRequestStatus status, WalletTopUpRequestStatus expectedStatus) {
        if (status != expectedStatus) {
            throw new IllegalStateException("Balans artırma sorğusunun statusu uyğun deyil.");
        }
    }

    static void requireExternalCompletableStatus(WalletTopUpRequestStatus status, String paymentProvider) {
        if ("manual".equals(paymentProvider) || (status != WalletTopUpRequestStatus.AWAITING_RECEIPT
                && status != WalletTopUpRequestStatus.EXPIRED && status != WalletTopUpRequestStatus.SUPERSEDED
                && status != WalletTopUpRequestStatus.PAYMENT_FAILED)) {
            throw new IllegalStateException("Balans artırma sorğusunun statusu uyğun deyil.");
        }
    }

    static void requireStatusIn(WalletTopUpRequestStatus status, WalletTopUpRequestStatus first, WalletTopUpRequestStatus second) {
        if (status != first && status != second) {
            throw new IllegalStateException("Balans artırma sorğusunun statusu uyğun deyil.");
        }
    }

    static void requireFraudReviewStatus(WalletTopUpRequestStatus status) {
        if (status != WalletTopUpRequestStatus.AUTO_CREDITED_PENDING_REVIEW
                && status != WalletTopUpRequestStatus.MANUAL_REVIEW
                && status != WalletTopUpRequestStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Balans artırma sorğusunun statusu uyğun deyil.");
        }
    }
}
