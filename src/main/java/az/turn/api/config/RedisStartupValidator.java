package az.turn.api;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "redis")
public class RedisStartupValidator implements ApplicationRunner {
    private final RedisConnectionFactory connectionFactory;

    public RedisStartupValidator(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String response = connection.ping();
            if (!"PONG".equalsIgnoreCase(response)) {
                throw new IllegalStateException("Redis PING cavabı düzgün deyil.");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Paylaşılan rate limit üçün Redis əlçatan deyil.", exception);
        }
    }
}
