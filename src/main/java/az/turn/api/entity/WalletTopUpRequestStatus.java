package az.turn.api;

public enum WalletTopUpRequestStatus {
    AWAITING_RECEIPT,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    EXPIRED;

    public boolean isActive() {
        return this == AWAITING_RECEIPT || this == PENDING_REVIEW;
    }
}
