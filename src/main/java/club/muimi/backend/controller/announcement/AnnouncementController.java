package club.muimi.backend.controller.announcement;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.service.announcement.AnnouncementService;
import club.muimi.backend.vo.announcement.AnnouncementVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public ApiResponse<List<AnnouncementVo>> listAnnouncements() {
        return ApiResponse.success(announcementService.listVisibleAnnouncements(), "ok");
    }

    @GetMapping("/{announcementId}")
    public ApiResponse<AnnouncementVo> getAnnouncement(@PathVariable Long announcementId) {
        return ApiResponse.success(announcementService.getAnnouncement(announcementId), "ok");
    }
}
