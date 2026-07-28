package club.muimi.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.task")
public class TaskModuleProperties {

    private DataSize attachmentMaxSize = DataSize.ofMegabytes(20);

    @PostConstruct
    public void validate() {
        if (attachmentMaxSize == null || attachmentMaxSize.toBytes() <= 0) {
            throw new IllegalStateException("任务附件最大大小必须大于 0");
        }
    }
}
