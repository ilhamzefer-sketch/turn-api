package az.turn.api;

public record AdminLoginResponse(
        String username,
        String role,
        String message,
        boolean mustChangeCredentials,
        String accessToken
) {
}
