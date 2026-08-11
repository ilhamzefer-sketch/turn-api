package az.turn.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "redis")
public class RedisRateLimitStore implements RateLimitStore {
    private static final long WINDOW_SECONDS = 60;
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitDecision consume(String key, int limit, long now) {
        long windowStart = now - (now % WINDOW_SECONDS);
        String redisKey = "turn-api:rate-limit:" + key + ":" + windowStart;
        Long countValue = redisTemplate.execute(INCREMENT_SCRIPT, List.of(redisKey), String.valueOf(WINDOW_SECONDS * 2));
        long count = countValue == null ? limit + 1L : countValue;
        return new RateLimitDecision(count <= limit, limit, (int) Math.max(0, limit - count), windowStart + WINDOW_SECONDS);
    }
}
