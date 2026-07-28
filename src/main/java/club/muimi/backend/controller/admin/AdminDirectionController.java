package club.muimi.backend.controller.admin;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.dto.admin.DirectionUpsertRequest;
import club.muimi.backend.service.direction.DirectionService;
import club.muimi.backend.vo.admin.AdminDirectionTreeVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/directions")
public class AdminDirectionController {

    private final DirectionService directionService;

    public AdminDirectionController(DirectionService directionService) {
        this.directionService = directionService;
    }

    @GetMapping
    public ApiResponse<List<AdminDirectionTreeVo>> listDirections() {
        return ApiResponse.success(directionService.listAdminTree(), "ok");
    }

    @PostMapping
    public ApiResponse<AdminDirectionTreeVo> createDirection(@Valid @RequestBody DirectionUpsertRequest request) {
        return ApiResponse.success(directionService.createDirection(request), "方向创建成功");
    }

    @PutMapping("/{directionId}")
    public ApiResponse<AdminDirectionTreeVo> updateDirection(
            @PathVariable Long directionId,
            @Valid @RequestBody DirectionUpsertRequest request
    ) {
        return ApiResponse.success(directionService.updateDirection(directionId, request), "方向更新成功");
    }

    @DeleteMapping("/{directionId}")
    public ApiResponse<Void> deleteDirection(@PathVariable Long directionId) {
        directionService.deleteDirection(directionId);
        return ApiResponse.success(null, "方向删除成功");
    }
}
