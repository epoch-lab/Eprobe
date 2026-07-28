package club.muimi.backend.vo.announcement;

import club.muimi.backend.common.enums.AnnouncementScope;

import java.time.OffsetDateTime;

public record AnnouncementVo(
        Long id,
        String title,
        String contentMarkdown,
        AnnouncementScope scope,
        Long groupId,
        String groupName,
        Long publisherUserId,
        String publisherUsername,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
