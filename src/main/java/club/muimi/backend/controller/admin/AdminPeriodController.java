package club.muimi.backend.controller.admin;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.dto.admin.PeriodConfigRequest;
import club.muimi.backend.dto.admin.SavePeriodsRequest;
import club.muimi.backend.service.period.PeriodService;
import club.muimi.backend.vo.admin.AdminPeriodVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/periods")
public class AdminPeriodController {

    private final PeriodService periodService;

    public AdminPeriodController(PeriodService periodService) {
        this.periodService = periodService;
    }

    @GetMapping
    public ApiResponse<List<AdminPeriodVo>> listPeriods() {
        return ApiResponse.success(periodService.listPeriods(), "ok");
    }

    @PostMapping
    public ApiResponse<List<AdminPeriodVo>> savePeriods(@Valid @RequestBody SavePeriodsRequest request) {
        return ApiResponse.success(periodService.savePeriods(request.periods()), "时期配置保存成功");
    }

    @PutMapping("/{periodId}")
    public ApiResponse<AdminPeriodVo> updatePeriod(
            @PathVariable Long periodId,
            @Valid @RequestBody PeriodConfigRequest request
    ) {
        return ApiResponse.success(periodService.updatePeriod(periodId, request), "时期配置更新成功");
    }
}
