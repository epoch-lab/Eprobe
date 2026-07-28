package club.muimi.backend.service.file;

import club.muimi.backend.common.enums.StoredFilePurpose;
import club.muimi.backend.dto.file.CreateUploadSessionRequest;
import club.muimi.backend.entity.StoredFile;
import club.muimi.backend.vo.file.StoredFileVo;
import club.muimi.backend.vo.file.UploadSessionVo;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    StoredFileVo uploadDirect(StoredFilePurpose purpose, MultipartFile file);

    UploadSessionVo createUploadSession(CreateUploadSessionRequest request);

    UploadSessionVo uploadChunk(Long sessionId, int chunkIndex, MultipartFile chunkFile);

    StoredFileVo completeUploadSession(Long sessionId);

    StoredFile requireOwnedUnboundFile(Long fileId, StoredFilePurpose purpose, Long uploaderUserId);

    void bindFile(StoredFile storedFile, String bindingType, Long bindingId);

    void deleteStoredFile(StoredFile storedFile);

    Resource loadAsResource(StoredFile storedFile);
}
