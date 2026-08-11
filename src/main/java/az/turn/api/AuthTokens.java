package az.turn.api;

public record AuthTokens(
        String accessToken,
        String refreshToken
) {
}
