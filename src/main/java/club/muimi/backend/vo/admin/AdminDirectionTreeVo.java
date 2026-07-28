package club.muimi.backend.vo.admin;

import java.util.List;

public record AdminDirectionTreeVo(
        Long id,
        Long parentId,
        String name,
        Integer level,
        Integer sortOrder,
        Boolean enabled,
        List<AdminDirectionTreeVo> children
) {
}
