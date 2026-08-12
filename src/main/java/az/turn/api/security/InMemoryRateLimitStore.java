package az.turn.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimitStore implements RateLimitStore {
    private static final long WINDOW_SECONDS = 60;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    @Override
    public synchronized RateLimitDecision consume(String key, int limit, long now) {
        long windowStart = now - (now % WINDOW_SECONDS);
        String windowKey = key + ":" + windowStart;
        WindowCounter current = counters.get(windowKey);
        int count = current == null ? 1 : current.count() + 1;
        counters.put(windowKey, new WindowCounter(windowStart, count));
        if (requestCounter.incrementAndGet() % 1000 == 0) {
            counters.entrySet().removeIf(entry -> entry.getValue().windowStart() + (WINDOW_SECONDS * 2) < now);
        }
        return new RateLimitDecision(count <= limit, limit, Math.max(0, limit - count), windowStart + WINDOW_SECONDS);
    }

    private record WindowCounter(long windowStart, int count) {
    }
}
