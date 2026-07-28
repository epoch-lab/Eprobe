package club.muimi.backend.vo.file;

import club.muimi.backend.common.enums.StoredFilePurpose;

public record StoredFileVo(
        Long fileId,
        StoredFilePurpose purpose,
        String originalFileName,
        String contentType,
        long sizeBytes
) {
}
