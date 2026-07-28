package club.muimi.backend.vo.dashboard;

public record DashboardOverviewVo(
        long totalUsers,
        long totalApplications,
        long groupedApplications,
        long ungroupedApplications,
        long totalGroups,
        long totalTasks,
        long totalSubmittedTaskResults,
        long totalReviewedTaskResults
) {
}
