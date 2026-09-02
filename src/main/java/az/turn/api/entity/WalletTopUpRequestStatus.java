package az.turn.api;

public enum WalletTopUpRequestStatus {
    AWAITING_RECEIPT,
    PENDING_REVIEW,
    MANUAL_REVIEW,
    AUTO_CREDITED_PENDING_REVIEW,
    APPROVED,
    VERIFIED,
    REJECTED,
    FRAUD_CONFIRMED,
    EXPIRED;

    public boolean isActive() {
        return this == AWAITING_RECEIPT
                || this == PENDING_REVIEW
                || this == MANUAL_REVIEW
                || this == AUTO_CREDITED_PENDING_REVIEW;
    }
}
