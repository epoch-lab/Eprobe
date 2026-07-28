package club.muimi.backend.vo.meta;

import club.muimi.backend.common.enums.PeriodType;

import java.time.OffsetDateTime;

public record CurrentPeriodVo(
        PeriodType currentPeriod,
        OffsetDateTime serverTime
) {
}
