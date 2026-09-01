package az.turn.api;

public class SecureUploadException extends RuntimeException {
    private final SecureUploadFailure failure;

    public SecureUploadException(SecureUploadFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public SecureUploadException(SecureUploadFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public SecureUploadFailure getFailure() {
        return failure;
    }
}
