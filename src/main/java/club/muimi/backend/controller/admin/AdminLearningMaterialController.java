package club.muimi.backend.controller.admin;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.dto.material.UpsertLearningMaterialRequest;
import club.muimi.backend.service.material.LearningMaterialService;
import club.muimi.backend.vo.material.LearningMaterialVo;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminLearningMaterialController {

    private final LearningMaterialService learningMaterialService;

    public AdminLearningMaterialController(LearningMaterialService learningMaterialService) {
        this.learningMaterialService = learningMaterialService;
    }

    @PostMapping("/api/v1/admin/groups/{groupId}/materials")
    @PreAuthorize("@authzService.canManageGroup(authentication, #groupId)")
    public ApiResponse<LearningMaterialVo> createMaterial(
            @PathVariable Long groupId,
            @Valid @RequestBody UpsertLearningMaterialRequest request
    ) {
        return ApiResponse.success(learningMaterialService.createMaterial(groupId, request), "学习资料创建成功");
    }

    @PutMapping("/api/v1/admin/groups/{groupId}/materials/{materialId}")
    @PreAuthorize("@authzService.canManageGroup(authentication, #groupId)")
    public ApiResponse<LearningMaterialVo> updateMaterial(
            @PathVariable Long groupId,
            @PathVariable Long materialId,
            @Valid @RequestBody UpsertLearningMaterialRequest request
    ) {
        return ApiResponse.success(learningMaterialService.updateMaterial(groupId, materialId, request), "学习资料更新成功");
    }

    @DeleteMapping("/api/v1/admin/groups/{groupId}/materials/{materialId}")
    @PreAuthorize("@authzService.canManageGroup(authentication, #groupId)")
    public ApiResponse<Void> deleteMaterial(@PathVariable Long groupId, @PathVariable Long materialId) {
        learningMaterialService.deleteMaterial(groupId, materialId);
        return ApiResponse.success(null, "学习资料删除成功");
    }
}
