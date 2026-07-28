package club.muimi.backend.vo.admin;

import club.muimi.backend.common.enums.PeriodType;

import java.time.OffsetDateTime;

public record AdminPeriodVo(
        Long id,
        PeriodType periodType,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        Boolean enabled
) {
}
