package club.muimi.backend.controller.direction;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.service.direction.DirectionService;
import club.muimi.backend.vo.direction.DirectionTreeVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/directions")
public class DirectionController {

    private final DirectionService directionService;

    public DirectionController(DirectionService directionService) {
        this.directionService = directionService;
    }

    @GetMapping
    public ApiResponse<List<DirectionTreeVo>> listDirections() {
        return ApiResponse.success(directionService.listPublicTree(true), "ok");
    }
}
