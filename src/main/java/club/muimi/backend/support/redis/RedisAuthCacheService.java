package club.muimi.backend.support.redis;

import club.muimi.backend.common.enums.EmailCodeScene;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "app.auth", name = "cache-type", havingValue = "redis")
public class RedisAuthCacheService implements AuthCacheService {

    private final StringRedisTemplate redisTemplate;

    public RedisAuthCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveEmailCode(EmailCodeScene scene, String email, String code, Duration ttl) {
        redisTemplate.opsForValue().set(emailCodeKey(scene, email), code, ttl);
    }

    @Override
    public Optional<String> getEmailCode(EmailCodeScene scene, String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(emailCodeKey(scene, email)));
    }

    @Override
    public void deleteEmailCode(EmailCodeScene scene, String email) {
        redisTemplate.delete(emailCodeKey(scene, email));
    }

    @Override
    public void markEmailCooldown(EmailCodeScene scene, String email, Duration ttl) {
        redisTemplate.opsForValue().set(emailCooldownKey(scene, email), "1", ttl);
    }

    @Override
    public boolean hasEmailCooldown(EmailCodeScene scene, String email) {
        Boolean result = redisTemplate.hasKey(emailCooldownKey(scene, email));
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void clearEmailCooldown(EmailCodeScene scene, String email) {
        redisTemplate.delete(emailCooldownKey(scene, email));
    }

    @Override
    public long incrementEmailSendIpCount(String clientIp, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(emailSendIpKey(clientIp));
        redisTemplate.expire(emailSendIpKey(clientIp), ttl);
        return value == null ? 0L : value;
    }

    @Override
    public long incrementEmailSendGlobalCount(Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(emailSendGlobalKey());
        redisTemplate.expire(emailSendGlobalKey(), ttl);
        return value == null ? 0L : value;
    }

    @Override
    public long incrementEmailCodeVerifyFailCount(EmailCodeScene scene, String email, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(emailCodeVerifyFailKey(scene, email));
        redisTemplate.expire(emailCodeVerifyFailKey(scene, email), ttl);
        return value == null ? 0L : value;
    }

    @Override
    public void clearEmailCodeVerifyFailCount(EmailCodeScene scene, String email) {
        redisTemplate.delete(emailCodeVerifyFailKey(scene, email));
    }

    @Override
    public void lockEmailCodeVerify(EmailCodeScene scene, String email, Duration ttl) {
        redisTemplate.opsForValue().set(emailCodeVerifyLockKey(scene, email), "1", ttl);
    }

    @Override
    public boolean isEmailCodeVerifyLocked(EmailCodeScene scene, String email) {
        Boolean result = redisTemplate.hasKey(emailCodeVerifyLockKey(scene, email));
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void clearEmailCodeVerifyLock(EmailCodeScene scene, String email) {
        redisTemplate.delete(emailCodeVerifyLockKey(scene, email));
    }

    @Override
    public long incrementLoginFailCount(String email, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(loginFailKey(email));
        redisTemplate.expire(loginFailKey(email), ttl);
        return value == null ? 0L : value;
    }

    @Override
    public void clearLoginFailCount(String email) {
        redisTemplate.delete(loginFailKey(email));
        redisTemplate.delete(loginLockKey(email));
    }

    @Override
    public void lockLogin(String email, Duration ttl) {
        redisTemplate.opsForValue().set(loginLockKey(email), "1", ttl);
    }

    @Override
    public boolean isLoginLocked(String email) {
        Boolean result = redisTemplate.hasKey(loginLockKey(email));
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void blacklistToken(String jti, Duration ttl) {
        redisTemplate.opsForValue().set(jwtBlacklistKey(jti), "1", ttl);
    }

    @Override
    public boolean isTokenBlacklisted(String jti) {
        Boolean result = redisTemplate.hasKey(jwtBlacklistKey(jti));
        return Boolean.TRUE.equals(result);
    }

    private String emailCodeKey(EmailCodeScene scene, String email) {
        return "auth:email-code:" + scene.name() + ":" + email;
    }

    private String emailCooldownKey(EmailCodeScene scene, String email) {
        return "auth:email-send-cooldown:" + scene.name() + ":" + email;
    }

    private String emailSendIpKey(String clientIp) {
        return "auth:email-send:ip:" + clientIp;
    }

    private String emailSendGlobalKey() {
        return "auth:email-send:global";
    }

    private String emailCodeVerifyFailKey(EmailCodeScene scene, String email) {
        return "auth:email-code:verify-fail:" + scene.name() + ":" + email;
    }

    private String emailCodeVerifyLockKey(EmailCodeScene scene, String email) {
        return "auth:email-code:verify-lock:" + scene.name() + ":" + email;
    }

    private String loginFailKey(String email) {
        return "auth:login:fail:" + email;
    }

    private String loginLockKey(String email) {
        return "auth:login:lock:" + email;
    }

    private String jwtBlacklistKey(String jti) {
        return "auth:jwt:blacklist:" + jti;
    }
}
