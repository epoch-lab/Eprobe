package club.muimi.backend.controller.file;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.common.enums.StoredFilePurpose;
import club.muimi.backend.dto.file.CreateUploadSessionRequest;
import club.muimi.backend.service.file.FileStorageService;
import club.muimi.backend.vo.file.StoredFileVo;
import club.muimi.backend.vo.file.UploadSessionVo;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<StoredFileVo> uploadDirect(
            @RequestParam StoredFilePurpose purpose,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(fileStorageService.uploadDirect(purpose, file), "文件上传成功");
    }

    @PostMapping("/sessions")
    public ApiResponse<UploadSessionVo> createUploadSession(@Valid @RequestBody CreateUploadSessionRequest request) {
        return ApiResponse.success(fileStorageService.createUploadSession(request), "上传会话创建成功");
    }

    @PostMapping(value = "/sessions/{sessionId}/chunks/{chunkIndex}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadSessionVo> uploadChunk(
            @PathVariable Long sessionId,
            @PathVariable int chunkIndex,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(fileStorageService.uploadChunk(sessionId, chunkIndex, file), "分片上传成功");
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public ApiResponse<StoredFileVo> completeUploadSession(@PathVariable Long sessionId) {
        return ApiResponse.success(fileStorageService.completeUploadSession(sessionId), "分片文件合并成功");
    }
}
