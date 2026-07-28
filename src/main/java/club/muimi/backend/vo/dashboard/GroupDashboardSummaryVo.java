package club.muimi.backend.vo.dashboard;

public record GroupDashboardSummaryVo(
        Long groupId,
        String groupName,
        long memberCount,
        long taskCount,
        long submittedCount,
        long reviewedCount,
        long pendingCount,
        double completionRate
) {
}
