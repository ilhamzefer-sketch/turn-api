package az.turn.api;

public record AdminLoginResponse(
        String username,
        String role,
        String message,
        String accessToken
) {
}
