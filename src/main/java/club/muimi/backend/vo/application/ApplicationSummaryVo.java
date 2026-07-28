package club.muimi.backend.vo.application;

import java.util.List;

public record ApplicationSummaryVo(
        long applicationCount,
        long submittedCount,
        long groupedCount,
        List<Long> groupIds
) {
}
