package club.muimi.backend.vo.direction;

import java.util.List;

public record DirectionTreeVo(
        Long id,
        String name,
        Integer level,
        List<DirectionTreeVo> children
) {
}
