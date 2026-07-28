package club.muimi.backend.controller.admin;

import club.muimi.backend.service.export.ExportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/admin/exports")
public class AdminExportController {

    private final ExportService exportService;

    public AdminExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/applications")
    public ResponseEntity<Resource> exportApplications() {
        return buildWorkbookResponse(exportService.exportApplications(), "applications.xlsx");
    }

    @GetMapping("/groups")
    public ResponseEntity<Resource> exportGroups() {
        return buildWorkbookResponse(exportService.exportAdminGroupMembers(), "group-members.xlsx");
    }

    @GetMapping("/groups/{groupId}/tasks")
    public ResponseEntity<Resource> exportGroupTasks(@PathVariable Long groupId) {
        return buildWorkbookResponse(exportService.exportManageableGroupTaskResults(groupId), "group-task-results-" + groupId + ".xlsx");
    }

    private ResponseEntity<Resource> buildWorkbookResponse(byte[] bytes, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new ByteArrayResource(bytes));
    }
}
