package club.muimi.backend.service.notification;

import club.muimi.backend.common.enums.NotificationType;
import club.muimi.backend.entity.Notification;
import club.muimi.backend.repository.NotificationRepository;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.service.user.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserService currentUserService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                userRepository,
                currentUserService,
                Clock.fixed(Instant.parse("2026-06-29T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void createOrRefreshShouldReuseExistingNotificationForSameBizKey() {
        Notification existing = Notification.builder()
                .id(9L)
                .recipientUserId(2L)
                .bizKey("task.reviewed:1:2")
                .type(NotificationType.TASK_PUBLISHED)
                .title("旧通知")
                .content("旧内容")
                .build();
        when(notificationRepository.findByRecipientUserIdAndBizKey(2L, "task.reviewed:1:2"))
                .thenReturn(Optional.of(existing));
        when(notificationRepository.save(existing)).thenReturn(existing);

        notificationService.createOrRefresh(new NotificationCommand(
                2L,
                1L,
                NotificationType.TASK_REVIEWED,
                "任务已评测",
                "新内容",
                "task.reviewed:1:2",
                "TASK",
                1L
        ));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(9L);
        assertThat(saved.getRecipientUserId()).isEqualTo(2L);
        assertThat(saved.getType()).isEqualTo(NotificationType.TASK_REVIEWED);
        assertThat(saved.getTitle()).isEqualTo("任务已评测");
        assertThat(saved.getContent()).isEqualTo("新内容");
        assertThat(saved.getSenderUserId()).isEqualTo(1L);
        assertThat(saved.getRelatedType()).isEqualTo("TASK");
        assertThat(saved.getRelatedId()).isEqualTo(1L);
    }
}
