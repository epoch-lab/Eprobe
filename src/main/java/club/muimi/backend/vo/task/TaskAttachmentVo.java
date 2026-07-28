package club.muimi.backend.vo.task;

public record TaskAttachmentVo(
        Long fileId,
        String originalFileName,
        String contentType,
        long sizeBytes
) {
}
