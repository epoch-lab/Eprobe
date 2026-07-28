package club.muimi.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

@Setter
@Getter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    private String secret;
    private long expireSeconds;
    private long rememberExpireSeconds;
    private String cookieName;
    private String csrfCookieName;
    private String csrfHeaderName;
    private boolean cookieSecure;
    private String cookieSameSite;

    @PostConstruct
    public void validate() {
        // 启动阶段直接拒绝危险配置，避免带着弱密钥或错误 Cookie 策略上线。
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("必须通过 app.security.jwt.secret 或 JWT_SECRET 配置 JWT 密钥");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("JWT 密钥长度不能少于 32 个字符");
        }
        if (expireSeconds <= 0 || rememberExpireSeconds <= 0) {
            throw new IllegalStateException("JWT 过期时间必须为正数");
        }
        if (rememberExpireSeconds < expireSeconds) {
            throw new IllegalStateException("记住我有效期不能短于普通登录有效期");
        }
        if (cookieName == null || cookieName.isBlank()) {
            throw new IllegalStateException("JWT Cookie 名称不能为空");
        }
        if (csrfCookieName == null || csrfCookieName.isBlank()) {
            throw new IllegalStateException("CSRF Cookie 名称不能为空");
        }
        if (csrfHeaderName == null || csrfHeaderName.isBlank()) {
            throw new IllegalStateException("CSRF Header 名称不能为空");
        }
        if (cookieSameSite == null || cookieSameSite.isBlank()) {
            throw new IllegalStateException("Cookie SameSite 策略不能为空");
        }
        if ("none".equals(cookieSameSite.toLowerCase(Locale.ROOT)) && !cookieSecure) {
            throw new IllegalStateException("SameSite=None 时必须同时启用 Secure Cookie");
        }
    }
}
