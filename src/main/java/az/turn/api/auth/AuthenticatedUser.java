package az.turn.api;

public record AuthenticatedUser(
        AuthUserType userType,
        Long userId,
        String username,
        Long sessionId
) {
    public AuthenticatedUser(AuthUserType userType, Long userId, String username) {
        this(userType, userId, username, null);
    }

    public boolean isAdmin() {
        return userType == AuthUserType.ADMIN;
    }

    public boolean isRegistration() {
        return userType == AuthUserType.REGISTRATION;
    }

    public boolean isCustomer() {
        return userType == AuthUserType.CUSTOMER;
    }

    public boolean isQueueManager() {
        return userType == AuthUserType.QUEUE_MANAGER;
    }

    public boolean isUser() {
        return userType == AuthUserType.USER;
    }
}
