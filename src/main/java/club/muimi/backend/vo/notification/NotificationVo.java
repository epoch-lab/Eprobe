package club.muimi.backend.vo.notification;

import club.muimi.backend.common.enums.NotificationType;

import java.time.OffsetDateTime;

public record NotificationVo(
        Long id,
        NotificationType type,
        String title,
        String content,
        String relatedType,
        Long relatedId,
        Long senderUserId,
        OffsetDateTime readAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
