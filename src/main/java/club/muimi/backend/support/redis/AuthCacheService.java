package club.muimi.backend.support.redis;

import club.muimi.backend.common.enums.EmailCodeScene;

import java.time.Duration;
import java.util.Optional;

public interface AuthCacheService {

    void saveEmailCode(EmailCodeScene scene, String email, String code, Duration ttl);

    Optional<String> getEmailCode(EmailCodeScene scene, String email);

    void deleteEmailCode(EmailCodeScene scene, String email);

    void markEmailCooldown(EmailCodeScene scene, String email, Duration ttl);

    boolean hasEmailCooldown(EmailCodeScene scene, String email);

    void clearEmailCooldown(EmailCodeScene scene, String email);

    long incrementEmailSendIpCount(String clientIp, Duration ttl);

    long incrementEmailSendGlobalCount(Duration ttl);

    long incrementEmailCodeVerifyFailCount(EmailCodeScene scene, String email, Duration ttl);

    void clearEmailCodeVerifyFailCount(EmailCodeScene scene, String email);

    void lockEmailCodeVerify(EmailCodeScene scene, String email, Duration ttl);

    boolean isEmailCodeVerifyLocked(EmailCodeScene scene, String email);

    void clearEmailCodeVerifyLock(EmailCodeScene scene, String email);

    long incrementLoginFailCount(String email, Duration ttl);

    void clearLoginFailCount(String email);

    void lockLogin(String email, Duration ttl);

    boolean isLoginLocked(String email);

    void blacklistToken(String jti, Duration ttl);

    boolean isTokenBlacklisted(String jti);
}
