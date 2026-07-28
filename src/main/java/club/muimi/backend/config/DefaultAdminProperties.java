package club.muimi.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "app.bootstrap.default-admin")
public class DefaultAdminProperties {

    private boolean enabled = true;
    private String username;
    private String password;
    private String email;

    public boolean isConfigured() {
        return hasText(username) && hasText(password) && hasText(email);
    }

    public void validateRequired() {
        requireConfigured(username, "DEFAULT_ADMIN_USERNAME");
        requireConfigured(password, "DEFAULT_ADMIN_PASSWORD");
        requireConfigured(email, "DEFAULT_ADMIN_EMAIL");
    }

    private void requireConfigured(String value, String envName) {
        if (!hasText(value)) {
            throw new IllegalStateException("默认管理员配置缺失，请设置环境变量 " + envName);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
