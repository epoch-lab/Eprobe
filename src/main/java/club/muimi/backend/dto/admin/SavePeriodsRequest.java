package club.muimi.backend.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SavePeriodsRequest(
        @NotEmpty(message = "时期配置不能为空")
        List<@Valid PeriodConfigRequest> periods
) {
}
