package club.muimi.backend.controller.support;

import club.muimi.backend.entity.StoredFile;
import club.muimi.backend.service.file.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class FileDownloadResponseBuilder {

    private final FileStorageService fileStorageService;

    public FileDownloadResponseBuilder(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public ResponseEntity<Resource> build(StoredFile storedFile) {
        Resource resource = fileStorageService.loadAsResource(storedFile);
        return ResponseEntity.ok()
                .contentType(parseContentType(storedFile.getContentType()))
                .contentLength(storedFile.getSizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(storedFile.getOriginalFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(resource);
    }

    private MediaType parseContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
