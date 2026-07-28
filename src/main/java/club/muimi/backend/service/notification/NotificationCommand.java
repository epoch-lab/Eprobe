package club.muimi.backend.service.notification;

import club.muimi.backend.common.enums.NotificationType;

public record NotificationCommand(
        Long recipientUserId,
        Long senderUserId,
        NotificationType type,
        String title,
        String content,
        String bizKey,
        String relatedType,
        Long relatedId
) {
}
