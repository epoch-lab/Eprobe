package club.muimi.backend.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

public record ApiResponse<T>(
        int code,
        String message,
        T data,
        long timestamp,
        String requestId
) {

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(0, message, data, Instant.now().toEpochMilli(), currentRequestId());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(
                errorCode.getCode(),
                message,
                data,
                Instant.now().toEpochMilli(),
                currentRequestId()
        );
    }

    private static String currentRequestId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "N/A";
        }
        HttpServletRequest request = attributes.getRequest();
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? "N/A" : requestId.toString();
    }
}
