package club.muimi.backend.config;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class RedisAvailabilityValidator {

    private final AuthProperties authProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisAvailabilityValidator(AuthProperties authProperties, StringRedisTemplate stringRedisTemplate) {
        this.authProperties = authProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @jakarta.annotation.PostConstruct
    public void validateRedisAvailability() {
        if (authProperties.getCacheType() != AuthProperties.CacheType.REDIS) {
            throw new IllegalStateException("当前版本仅支持 Redis 作为认证缓存");
        }

        RedisConnectionFactory connectionFactory = stringRedisTemplate.getRequiredConnectionFactory();
        try (RedisConnection connection = connectionFactory.getConnection()) {
            String pong = connection.ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("Redis 连接校验失败，PING 未返回 PONG");
            }
            validateRedisWriteCapability();
        } catch (Exception exception) {
            throw new IllegalStateException("认证缓存 Redis 不可用，系统已拒绝启动，请检查 Redis 连接配置与服务状态", exception);
        }
    }

    private void validateRedisWriteCapability() {
        String key = "auth:startup-check:" + UUID.randomUUID();
        stringRedisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(10));
        stringRedisTemplate.delete(key);
    }
}
