package club.muimi.backend.service.notification;

import club.muimi.backend.common.api.PageResult;
import club.muimi.backend.entity.Notification;
import club.muimi.backend.entity.User;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.repository.NotificationRepository;
import club.muimi.backend.repository.UserRepository;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.notification.NotificationSummaryVo;
import club.muimi.backend.vo.notification.NotificationVo;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final Clock appClock;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            Clock appClock
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.appClock = appClock;
    }

    @Transactional
    public void createOrRefresh(NotificationCommand command) {
        if (command.recipientUserId() == null || command.bizKey() == null || command.bizKey().isBlank()) {
            return;
        }
        Notification notification = notificationRepository.findByRecipientUserIdAndBizKey(command.recipientUserId(), command.bizKey())
                .orElse(Notification.builder()
                        .recipientUserId(command.recipientUserId())
                        .bizKey(command.bizKey())
                        .build());
        notification.setSenderUserId(command.senderUserId());
        notification.setType(command.type());
        notification.setTitle(command.title());
        notification.setContent(command.content());
        notification.setRelatedType(command.relatedType());
        notification.setRelatedId(command.relatedId());
        if (notification.getId() == null) {
            notification.setReadAt(null);
        }
        notificationRepository.save(notification);
    }

    @Transactional
    public void createOrRefreshAll(Collection<NotificationCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        List<NotificationCommand> validCommands = commands.stream()
                .filter(Objects::nonNull)
                .filter(command -> command.recipientUserId() != null)
                .filter(command -> command.bizKey() != null && !command.bizKey().isBlank())
                .toList();
        if (validCommands.isEmpty()) {
            return;
        }
        Set<Long> recipientIds = validCommands.stream()
                .map(NotificationCommand::recipientUserId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> bizKeys = validCommands.stream()
                .map(NotificationCommand::bizKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, Notification> existingMap = notificationRepository.findAllByRecipientUserIdInAndBizKeyIn(recipientIds, bizKeys).stream()
                .collect(java.util.stream.Collectors.toMap(
                        notification -> compositeKey(notification.getRecipientUserId(), notification.getBizKey()),
                        notification -> notification
                ));
        for (NotificationCommand command : validCommands) {
            String key = compositeKey(command.recipientUserId(), command.bizKey());
            Notification notification = existingMap.getOrDefault(key, Notification.builder()
                    .recipientUserId(command.recipientUserId())
                    .bizKey(command.bizKey())
                    .build());
            notification.setSenderUserId(command.senderUserId());
            notification.setType(command.type());
            notification.setTitle(command.title());
            notification.setContent(command.content());
            notification.setRelatedType(command.relatedType());
            notification.setRelatedId(command.relatedId());
            if (notification.getId() == null) {
                notification.setReadAt(null);
            }
            Notification saved = notificationRepository.save(notification);
            existingMap.put(key, saved);
        }
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationVo> listCurrentUserNotifications(int page, int size, boolean unreadOnly) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        return PageResult.from(
                notificationRepository.findPageByRecipientUserId(
                        currentUser.getUserId(),
                        unreadOnly,
                        PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"))
                ).map(this::toVo)
        );
    }

    @Transactional(readOnly = true)
    public NotificationSummaryVo getCurrentUserNotificationSummary() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        long unreadCount = notificationRepository.countByRecipientUserIdAndReadAtIsNull(currentUser.getUserId());
        return new NotificationSummaryVo(unreadCount);
    }

    @Transactional
    public void markCurrentUserNotificationRead(Long notificationId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("通知不存在"));
        if (!notification.getRecipientUserId().equals(currentUser.getUserId())) {
            throw new ForbiddenException("无权操作该通知");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now(appClock));
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllCurrentUserNotificationsRead() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        List<Notification> notifications = notificationRepository.findAllByRecipientUserIdAndReadAtIsNull(currentUser.getUserId());
        if (notifications.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(appClock);
        notifications.forEach(notification -> notification.setReadAt(now));
        notificationRepository.saveAll(notifications);
    }

    @Transactional(readOnly = true)
    public List<Long> findAllActiveUserIds() {
        return userRepository.findAll().stream()
                .filter(user -> user.getStatus() == club.muimi.backend.common.enums.UserStatus.ACTIVE)
                .map(User::getId)
                .toList();
    }

    private NotificationVo toVo(Notification notification) {
        return new NotificationVo(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getRelatedType(),
                notification.getRelatedId(),
                notification.getSenderUserId(),
                notification.getReadAt() == null ? null : notification.getReadAt().atZone(appClock.getZone()).toOffsetDateTime(),
                notification.getCreatedAt().atZone(appClock.getZone()).toOffsetDateTime(),
                notification.getUpdatedAt().atZone(appClock.getZone()).toOffsetDateTime()
        );
    }

    private String compositeKey(Long recipientUserId, String bizKey) {
        return recipientUserId + "|" + bizKey;
    }
}
