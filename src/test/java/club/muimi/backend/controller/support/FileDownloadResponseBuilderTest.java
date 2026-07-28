package club.muimi.backend.controller.support;

import club.muimi.backend.entity.StoredFile;
import club.muimi.backend.service.file.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileDownloadResponseBuilderTest {

    @Test
    void buildShouldFallbackToOctetStreamWhenStoredContentTypeIsInvalid() {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        FileDownloadResponseBuilder builder = new FileDownloadResponseBuilder(fileStorageService);
        StoredFile storedFile = StoredFile.builder()
                .id(1L)
                .originalFileName("guide.pdf")
                .contentType("not a valid media type")
                .sizeBytes(4)
                .storagePath("files/guide.pdf")
                .build();
        when(fileStorageService.loadAsResource(storedFile)).thenReturn(new ByteArrayResource("test".getBytes()));

        ResponseEntity<?> response = builder.build(storedFile);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }
}
