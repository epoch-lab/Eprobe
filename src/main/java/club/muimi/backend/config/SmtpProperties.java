package club.muimi.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "spring.mail")
public class SmtpProperties {

    private String host;
    private Integer port;
    private String username;
    private String password;

    @PostConstruct
    public void validate() {
        // 邮件账号、SMTP 主机和授权码缺一不可，启动时直接校验，避免业务流程走到一半才失败。
        requireConfigured(host, "MAIL_SMTP_HOST");
        requireConfigured(username, "MAIL_ACCOUNT");
        requireConfigured(password, "MAIL_AUTH_CODE");
    }

    private void requireConfigured(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("邮件服务配置缺失，请设置环境变量 " + envName);
        }
    }
}
