package club.muimi.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class FileStorageProperties {

    private String rootPath = "./storage";
    private DataSize chunkSize = DataSize.ofBytes(0);
    private boolean cleanupEnabled = true;
    private Duration tempSessionTtl = Duration.ofHours(24);
    private Duration orphanFileTtl = Duration.ofHours(24);
    private List<String> allowedExtensions = new ArrayList<>();
    private List<String> allowedContentTypes = new ArrayList<>();

    @PostConstruct
    public void validate() {
        if (rootPath == null || rootPath.isBlank()) {
            throw new IllegalStateException("文件存储根目录不能为空");
        }
        if (chunkSize == null || chunkSize.toBytes() < 0) {
            throw new IllegalStateException("分片上传大小不能小于 0");
        }
        if (tempSessionTtl == null || tempSessionTtl.isNegative() || tempSessionTtl.isZero()) {
            throw new IllegalStateException("上传会话清理时间必须大于 0");
        }
        if (orphanFileTtl == null || orphanFileTtl.isNegative() || orphanFileTtl.isZero()) {
            throw new IllegalStateException("孤儿文件清理时间必须大于 0");
        }
    }
}
