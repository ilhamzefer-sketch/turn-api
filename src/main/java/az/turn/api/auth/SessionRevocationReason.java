package az.turn.api;

public enum SessionRevocationReason {
    LOGOUT,
    MANUAL_REVOCATION,
    IDLE_TIMEOUT,
    ABSOLUTE_TIMEOUT,
    TOKEN_REUSE,
    ACCOUNT_DISABLED,
    CREDENTIALS_CHANGED,
    EXPIRED,
    SECURITY_EVENT
}
