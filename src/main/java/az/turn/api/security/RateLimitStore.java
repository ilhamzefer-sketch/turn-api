package az.turn.api;

public interface RateLimitStore {
    RateLimitDecision consume(String key, int limit, long nowEpochSeconds);
}
