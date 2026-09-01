package az.turn.api;

public class WalletTopUpException extends RuntimeException {
    private final WalletTopUpFailure failure;

    public WalletTopUpException(WalletTopUpFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public WalletTopUpFailure getFailure() {
        return failure;
    }
}
