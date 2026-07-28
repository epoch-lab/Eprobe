package club.muimi.backend.common.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResult<T>(
        List<T> list,
        int page,
        int size,
        long total,
        int totalPages
) {

    public static <T> PageResult<T> from(Page<T> page) {
        return new PageResult<>(
                page.getContent(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
