package club.muimi.backend.controller.leader;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.dto.announcement.UpsertAnnouncementRequest;
import club.muimi.backend.service.announcement.AnnouncementService;
import club.muimi.backend.vo.announcement.AnnouncementVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/leader/announcements")
public class LeaderAnnouncementController {

    private final AnnouncementService announcementService;

    public LeaderAnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @PostMapping
    public ApiResponse<AnnouncementVo> createAnnouncement(@Valid @RequestBody UpsertAnnouncementRequest request) {
        return ApiResponse.success(announcementService.createAnnouncement(request), "公告创建成功");
    }

    @PutMapping("/{announcementId}")
    public ApiResponse<AnnouncementVo> updateAnnouncement(
            @PathVariable Long announcementId,
            @Valid @RequestBody UpsertAnnouncementRequest request
    ) {
        return ApiResponse.success(announcementService.updateAnnouncement(announcementId, request), "公告更新成功");
    }

    @DeleteMapping("/{announcementId}")
    public ApiResponse<Void> deleteAnnouncement(@PathVariable Long announcementId) {
        announcementService.deleteAnnouncement(announcementId);
        return ApiResponse.success(null, "公告删除成功");
    }
}
