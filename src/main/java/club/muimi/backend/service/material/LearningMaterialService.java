package club.muimi.backend.service.material;

import club.muimi.backend.common.enums.*;
import club.muimi.backend.dto.material.UpsertLearningMaterialRequest;
import club.muimi.backend.entity.*;
import club.muimi.backend.exception.ForbiddenException;
import club.muimi.backend.exception.NotFoundException;
import club.muimi.backend.exception.ValidationException;
import club.muimi.backend.repository.*;
import club.muimi.backend.security.auth.LoginUser;
import club.muimi.backend.service.audit.AuditLogCommand;
import club.muimi.backend.service.audit.AuditLogService;
import club.muimi.backend.service.file.FileStorageService;
import club.muimi.backend.service.notification.NotificationCommand;
import club.muimi.backend.service.notification.NotificationService;
import club.muimi.backend.service.user.CurrentUserService;
import club.muimi.backend.vo.material.LearningMaterialVo;
import club.muimi.backend.vo.task.TaskAttachmentVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LearningMaterialService {

    private static final String MATERIAL_BINDING_TYPE = "MATERIAL";

    private final LearningMaterialRepository learningMaterialRepository;
    private final RecruitmentGroupRepository recruitmentGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public LearningMaterialService(
            LearningMaterialRepository learningMaterialRepository,
            RecruitmentGroupRepository recruitmentGroupRepository,
            GroupMemberRepository groupMemberRepository,
            StoredFileRepository storedFileRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            FileStorageService fileStorageService,
            NotificationService notificationService,
            AuditLogService auditLogService,
            Clock appClock
    ) {
        this.learningMaterialRepository = learningMaterialRepository;
        this.recruitmentGroupRepository = recruitmentGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.storedFileRepository = storedFileRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.fileStorageService = fileStorageService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.appClock = appClock;
    }

    @Transactional(readOnly = true)
    public List<LearningMaterialVo> listVisibleMaterials() {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        if (currentUser.getRole() == Role.ADMIN) {
            return buildMaterialVos(learningMaterialRepository.findAll());
        }
        Set<Long> groupIds = new LinkedHashSet<>();
        groupIds.addAll(groupMemberRepository.findAllByUserId(currentUser.getUserId()).stream().map(GroupMember::getGroupId).toList());
        groupIds.addAll(recruitmentGroupRepository.findAllByLeaderUserId(currentUser.getUserId()).stream().map(RecruitmentGroup::getId).toList());
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return buildMaterialVos(learningMaterialRepository.findAllByGroupIdInOrderByCreatedAtDesc(groupIds));
    }

    @Transactional(readOnly = true)
    public LearningMaterialVo getMaterial(Long materialId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        LearningMaterial material = learningMaterialRepository.findById(materialId)
                .orElseThrow(() -> new NotFoundException("学习资料不存在"));
        ensureCanViewMaterial(currentUser, material);
        return buildMaterialVos(List.of(material)).getFirst();
    }

    @Transactional
    public LearningMaterialVo createMaterial(Long groupId, UpsertLearningMaterialRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        ensureCanManageGroup(currentUser, groupId);
        validateMaterialPayload(request.contentMarkdown(), request.attachmentFileId() != null);
        StoredFile attachment = request.attachmentFileId() == null
                ? null
                : fileStorageService.requireOwnedUnboundFile(
                request.attachmentFileId(),
                StoredFilePurpose.MATERIAL_ATTACHMENT,
                currentUser.getUserId()
        );
        LearningMaterial material = learningMaterialRepository.save(LearningMaterial.builder()
                .groupId(groupId)
                .title(request.title().trim())
                .contentMarkdown(blankToNull(request.contentMarkdown()))
                .attachmentFileId(attachment == null ? null : attachment.getId())
                .publisherUserId(currentUser.getUserId())
                .build());
        if (attachment != null) {
            fileStorageService.bindFile(attachment, MATERIAL_BINDING_TYPE, material.getId());
        }
        notifyMaterialPublished(currentUser, material);
        recordMaterialAudit("CREATE_MATERIAL", "创建学习资料", currentUser, material);
        return buildMaterialVos(List.of(material)).getFirst();
    }

    @Transactional
    public LearningMaterialVo updateMaterial(Long groupId, Long materialId, UpsertLearningMaterialRequest request) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        ensureCanManageGroup(currentUser, groupId);
        LearningMaterial material = learningMaterialRepository.findById(materialId)
                .orElseThrow(() -> new NotFoundException("学习资料不存在"));
        if (!material.getGroupId().equals(groupId)) {
            throw new NotFoundException("学习资料不属于指定分组");
        }
        StoredFile oldAttachment = material.getAttachmentFileId() == null
                ? null
                : storedFileRepository.findById(material.getAttachmentFileId()).orElse(null);
        StoredFile newAttachment = request.attachmentFileId() == null
                ? null
                : fileStorageService.requireOwnedUnboundFile(
                request.attachmentFileId(),
                StoredFilePurpose.MATERIAL_ATTACHMENT,
                currentUser.getUserId()
        );
        boolean willHaveAttachment = newAttachment != null || (!request.removeAttachment() && oldAttachment != null);
        validateMaterialPayload(request.contentMarkdown(), willHaveAttachment);
        material.setTitle(request.title().trim());
        material.setContentMarkdown(blankToNull(request.contentMarkdown()));
        if (newAttachment != null) {
            material.setAttachmentFileId(newAttachment.getId());
        } else if (request.removeAttachment()) {
            material.setAttachmentFileId(null);
        }
        LearningMaterial saved = learningMaterialRepository.save(material);
        if (newAttachment != null) {
            fileStorageService.bindFile(newAttachment, MATERIAL_BINDING_TYPE, saved.getId());
        }
        if ((newAttachment != null || request.removeAttachment())
                && oldAttachment != null
                && !Objects.equals(saved.getAttachmentFileId(), oldAttachment.getId())) {
            fileStorageService.deleteStoredFile(oldAttachment);
        }
        recordMaterialAudit("UPDATE_MATERIAL", "更新学习资料", currentUser, saved);
        return buildMaterialVos(List.of(saved)).getFirst();
    }

    @Transactional
    public void deleteMaterial(Long groupId, Long materialId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        ensureCanManageGroup(currentUser, groupId);
        LearningMaterial material = learningMaterialRepository.findById(materialId)
                .orElseThrow(() -> new NotFoundException("学习资料不存在"));
        if (!material.getGroupId().equals(groupId)) {
            throw new NotFoundException("学习资料不属于指定分组");
        }
        if (material.getAttachmentFileId() != null) {
            StoredFile storedFile = storedFileRepository.findById(material.getAttachmentFileId()).orElse(null);
            material.setAttachmentFileId(null);
            learningMaterialRepository.save(material);
            if (storedFile != null) {
                fileStorageService.deleteStoredFile(storedFile);
            }
        }
        learningMaterialRepository.delete(material);
        recordMaterialAudit("DELETE_MATERIAL", "删除学习资料", currentUser, material);
    }

    @Transactional(readOnly = true)
    public StoredFile getMaterialAttachmentFile(Long materialId) {
        LoginUser currentUser = currentUserService.requireCurrentUser();
        LearningMaterial material = learningMaterialRepository.findById(materialId)
                .orElseThrow(() -> new NotFoundException("学习资料不存在"));
        ensureCanViewMaterial(currentUser, material);
        if (material.getAttachmentFileId() == null) {
            throw new NotFoundException("当前学习资料没有附件");
        }
        return storedFileRepository.findById(material.getAttachmentFileId())
                .orElseThrow(() -> new NotFoundException("附件不存在"));
    }

    private void ensureCanManageGroup(LoginUser currentUser, Long groupId) {
        RecruitmentGroup group = recruitmentGroupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("分组不存在"));
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (currentUser.getRole() != Role.LEADER || !Objects.equals(group.getLeaderUserId(), currentUser.getUserId())) {
            throw new ForbiddenException("当前用户无权管理该分组学习资料");
        }
    }

    private void ensureCanViewMaterial(LoginUser currentUser, LearningMaterial material) {
        if (currentUser.getRole() == Role.ADMIN) {
            return;
        }
        if (groupMemberRepository.existsByUserIdAndGroupId(currentUser.getUserId(), material.getGroupId())) {
            return;
        }
        if (recruitmentGroupRepository.existsByIdAndLeaderUserId(material.getGroupId(), currentUser.getUserId())) {
            return;
        }
        throw new ForbiddenException("当前用户无权查看该学习资料");
    }

    private void validateMaterialPayload(String contentMarkdown, boolean hasAttachment) {
        if ((contentMarkdown == null || contentMarkdown.isBlank()) && !hasAttachment) {
            throw new ValidationException("资料内容和资料附件不能同时为空");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<LearningMaterialVo> buildMaterialVos(List<LearningMaterial> materials) {
        if (materials.isEmpty()) {
            return List.of();
        }
        Map<Long, RecruitmentGroup> groupMap = recruitmentGroupRepository.findAllByIdIn(
                        materials.stream().map(LearningMaterial::getGroupId).collect(Collectors.toCollection(LinkedHashSet::new))
                ).stream()
                .collect(Collectors.toMap(RecruitmentGroup::getId, Function.identity()));
        Map<Long, User> userMap = userRepository.findAllById(
                        materials.stream().map(LearningMaterial::getPublisherUserId).collect(Collectors.toCollection(LinkedHashSet::new))
                ).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Long> fileIds = materials.stream().map(LearningMaterial::getAttachmentFileId).filter(Objects::nonNull).toList();
        Map<Long, StoredFile> fileMap = fileIds.isEmpty()
                ? Map.of()
                : storedFileRepository.findAllByIdIn(fileIds).stream()
                .collect(Collectors.toMap(StoredFile::getId, Function.identity()));
        return materials.stream()
                .map(material -> {
                    RecruitmentGroup group = groupMap.get(material.getGroupId());
                    User publisher = userMap.get(material.getPublisherUserId());
                    StoredFile file = material.getAttachmentFileId() == null ? null : fileMap.get(material.getAttachmentFileId());
                    return new LearningMaterialVo(
                            material.getId(),
                            material.getGroupId(),
                            group == null ? null : group.getName(),
                            material.getTitle(),
                            material.getContentMarkdown(),
                            file == null ? null : new TaskAttachmentVo(file.getId(), file.getOriginalFileName(), file.getContentType(), file.getSizeBytes()),
                            material.getPublisherUserId(),
                            publisher == null ? null : publisher.getUsername(),
                            material.getCreatedAt().atZone(appClock.getZone()).toOffsetDateTime(),
                            material.getUpdatedAt().atZone(appClock.getZone()).toOffsetDateTime()
                    );
                })
                .toList();
    }

    private void notifyMaterialPublished(LoginUser actor, LearningMaterial material) {
        List<NotificationCommand> commands = groupMemberRepository.findAllByGroupId(material.getGroupId()).stream()
                .map(GroupMember::getUserId)
                .filter(userId -> !Objects.equals(userId, actor.getUserId()))
                .distinct()
                .map(userId -> new NotificationCommand(
                        userId,
                        actor.getUserId(),
                        NotificationType.MATERIAL_PUBLISHED,
                        "组内资料已发布",
                        material.getTitle(),
                        "material.published:" + material.getId(),
                        "MATERIAL",
                        material.getId()
                ))
                .toList();
        notificationService.createOrRefreshAll(commands);
    }

    private void recordMaterialAudit(String action, String summary, LoginUser actor, LearningMaterial material) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("groupId", material.getGroupId());
        detail.put("title", material.getTitle());
        detail.put("hasAttachment", material.getAttachmentFileId() != null);
        auditLogService.record(AuditLogCommand.builder(
                        AuditModule.MATERIAL,
                        action,
                        AuditSeverity.IMPORTANT,
                        summary
                ).actor(actor)
                .target("MATERIAL", material.getId())
                .detail(detail)
                .build());
    }
}
