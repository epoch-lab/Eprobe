package club.muimi.backend.controller.material;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.controller.support.FileDownloadResponseBuilder;
import club.muimi.backend.service.material.LearningMaterialService;
import club.muimi.backend.vo.material.LearningMaterialVo;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/materials")
public class LearningMaterialController {

    private final LearningMaterialService learningMaterialService;
    private final FileDownloadResponseBuilder fileDownloadResponseBuilder;

    public LearningMaterialController(
            LearningMaterialService learningMaterialService,
            FileDownloadResponseBuilder fileDownloadResponseBuilder
    ) {
        this.learningMaterialService = learningMaterialService;
        this.fileDownloadResponseBuilder = fileDownloadResponseBuilder;
    }

    @GetMapping
    public ApiResponse<List<LearningMaterialVo>> listMaterials() {
        return ApiResponse.success(learningMaterialService.listVisibleMaterials(), "ok");
    }

    @GetMapping("/{materialId}")
    public ApiResponse<LearningMaterialVo> getMaterial(@PathVariable Long materialId) {
        return ApiResponse.success(learningMaterialService.getMaterial(materialId), "ok");
    }

    @GetMapping("/{materialId}/attachment")
    public ResponseEntity<Resource> downloadMaterialAttachment(@PathVariable Long materialId) {
        return fileDownloadResponseBuilder.build(learningMaterialService.getMaterialAttachmentFile(materialId));
    }
}
