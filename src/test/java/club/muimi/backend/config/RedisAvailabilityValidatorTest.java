package club.muimi.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisAvailabilityValidatorTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private RedisConnectionFactory redisConnectionFactory;
    @Mock
    private RedisConnection redisConnection;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @Test
    void shouldPassWhenRedisPingReturnsPongAndWriteProbeSucceeds() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setCacheType(AuthProperties.CacheType.REDIS);
        RedisAvailabilityValidator validator = new RedisAvailabilityValidator(authProperties, stringRedisTemplate);
        when(stringRedisTemplate.getRequiredConnectionFactory()).thenReturn(redisConnectionFactory);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        assertThatCode(validator::validateRedisAvailability).doesNotThrowAnyException();
    }

    @Test
    void shouldFailFastWhenRedisCanPingButCannotWrite() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setCacheType(AuthProperties.CacheType.REDIS);
        RedisAvailabilityValidator validator = new RedisAvailabilityValidator(authProperties, stringRedisTemplate);
        when(stringRedisTemplate.getRequiredConnectionFactory()).thenReturn(redisConnectionFactory);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.doThrow(new IllegalStateException("write disabled"))
                .when(valueOperations)
                .set(any(String.class), eq("1"), any(Duration.class));

        assertThatThrownBy(validator::validateRedisAvailability)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("认证缓存 Redis 不可用")
                .hasRootCauseMessage("write disabled");
    }

    @Test
    void shouldFailFastWhenRedisUnavailable() {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setCacheType(AuthProperties.CacheType.REDIS);
        RedisAvailabilityValidator validator = new RedisAvailabilityValidator(authProperties, stringRedisTemplate);
        when(stringRedisTemplate.getRequiredConnectionFactory()).thenReturn(redisConnectionFactory);
        when(redisConnectionFactory.getConnection()).thenThrow(new IllegalStateException("connect failed"));

        assertThatThrownBy(validator::validateRedisAvailability)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("认证缓存 Redis 不可用");
    }
}
