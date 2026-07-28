package club.muimi.backend.controller.meta;

import club.muimi.backend.common.api.ApiResponse;
import club.muimi.backend.service.period.PeriodService;
import club.muimi.backend.vo.meta.CurrentPeriodVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final PeriodService periodService;
    private final Clock appClock;

    public MetaController(PeriodService periodService, Clock appClock) {
        this.periodService = periodService;
        this.appClock = appClock;
    }

    @GetMapping("/current-period")
    public ApiResponse<CurrentPeriodVo> getCurrentPeriod() {
        CurrentPeriodVo result = new CurrentPeriodVo(
                periodService.getCurrentPeriod(),
                OffsetDateTime.now(appClock)
        );
        return ApiResponse.success(result, "ok");
    }
}
