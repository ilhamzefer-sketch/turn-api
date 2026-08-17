package az.turn.api;

public enum UserLoginFailure {
    NONE,
    INVALID_CREDENTIALS,
    LOCKED,
    REGISTRATION_REQUIRED,
    PASSWORD_RESET_REQUIRED,
    SUSPENDED
}
