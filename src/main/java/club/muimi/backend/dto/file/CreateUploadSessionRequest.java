package club.muimi.backend.dto.file;

import club.muimi.backend.common.enums.StoredFilePurpose;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUploadSessionRequest(
        @NotNull(message = "上传用途不能为空")
        StoredFilePurpose purpose,
        @NotBlank(message = "文件名不能为空")
        @Size(max = 255, message = "文件名长度不能超过 255 个字符")
        String fileName,
        @Size(max = 255, message = "文件类型长度不能超过 255 个字符")
        String contentType,
        @NotNull(message = "文件总大小不能为空")
        @Min(value = 1, message = "文件总大小必须大于 0")
        Long totalSize
) {
}
