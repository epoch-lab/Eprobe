package club.muimi.backend.dto.admin;

import club.muimi.backend.common.enums.PeriodType;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record PeriodConfigRequest(
        @NotNull(message = "时期类型不能为空")
        PeriodType periodType,
        @NotNull(message = "开始时间不能为空")
        OffsetDateTime startTime,
        @NotNull(message = "结束时间不能为空")
        OffsetDateTime endTime,
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
