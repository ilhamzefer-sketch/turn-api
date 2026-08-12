package az.turn.api;

public record RateLimitDecision(boolean allowed, int limit, int remaining, long resetEpochSeconds) {
}
