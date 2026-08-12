package az.turn.api;

public record AuthenticatedUser(
        AuthUserType userType,
        Long userId,
        String username
) {
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
}
