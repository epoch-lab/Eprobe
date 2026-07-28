package club.muimi.backend.vo.dashboard;

import club.muimi.backend.vo.task.ManageTaskVo;

import java.util.List;

public record GroupDashboardDetailVo(
        Long groupId,
        String groupName,
        long memberCount,
        long taskCount,
        long submittedCount,
        long reviewedCount,
        long pendingCount,
        double completionRate,
        List<ManageTaskVo> tasks
) {
}
