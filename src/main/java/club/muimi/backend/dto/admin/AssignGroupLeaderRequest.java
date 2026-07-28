package club.muimi.backend.dto.admin;

import jakarta.validation.constraints.NotNull;

public record AssignGroupLeaderRequest(
        @NotNull(message = "负责人用户 ID 不能为空")
        Long leaderUserId
) {
}
