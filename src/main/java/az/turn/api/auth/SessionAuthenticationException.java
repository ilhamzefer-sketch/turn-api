package az.turn.api;

public class SessionAuthenticationException extends RuntimeException {
    private final SessionState state;

    public SessionAuthenticationException(SessionState state, String message) {
        super(message);
        this.state = state;
    }

    public SessionState getState() {
        return state;
    }
}
