package az.turn.api;

public enum WalletTransactionType {
    ADMIN_CREDIT(WalletTransactionDirection.CREDIT),
    TOP_UP(WalletTransactionDirection.CREDIT),
    TOP_UP_REVERSAL(WalletTransactionDirection.DEBIT),
    SUBSCRIPTION_PAYMENT(WalletTransactionDirection.DEBIT),
    REFUND(WalletTransactionDirection.CREDIT);

    private final WalletTransactionDirection direction;

    WalletTransactionType(WalletTransactionDirection direction) {
        this.direction = direction;
    }

    public WalletTransactionDirection direction() {
        return direction;
    }
}
