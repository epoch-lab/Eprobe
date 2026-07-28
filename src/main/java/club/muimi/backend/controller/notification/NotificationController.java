package club.muimi.backend.controller.notification;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.common.api.PageResult;
import club.muimi.backend.service.notification.NotificationService;
import club.muimi.backend.vo.notification.NotificationSummaryVo;
import club.muimi.backend.vo.notification.NotificationVo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResult<NotificationVo>> listNotifications(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        return ApiResponse.success(notificationService.listCurrentUserNotifications(page, size, unreadOnly), "ok");
    }

    @GetMapping("/summary")
    public ApiResponse<NotificationSummaryVo> getSummary() {
        return ApiResponse.success(notificationService.getCurrentUserNotificationSummary(), "ok");
    }

    @PostMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@PathVariable Long notificationId) {
        notificationService.markCurrentUserNotificationRead(notificationId);
        return ApiResponse.success(null, "通知已标记为已读");
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllCurrentUserNotificationsRead();
        return ApiResponse.success(null, "全部通知已标记为已读");
    }
}
