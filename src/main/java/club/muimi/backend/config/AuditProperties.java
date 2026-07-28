package club.muimi.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.audit")
public class AuditProperties {

    private String majorEventLogFilePath = "./logs/business-audit.log";

    @PostConstruct
    public void validate() {
        if (majorEventLogFilePath == null || majorEventLogFilePath.isBlank()) {
            throw new IllegalStateException("重大事件日志文件路径不能为空");
        }
    }
}
