package club.muimi.backend.common.api;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    BAD_REQUEST(40000, HttpStatus.BAD_REQUEST, "参数错误"),
    UNAUTHORIZED(40100, HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    FORBIDDEN(40300, HttpStatus.FORBIDDEN, "无权限访问该资源"),
    PERIOD_NOT_ALLOWED(40310, HttpStatus.FORBIDDEN, "当前时期不允许该操作"),
    NOT_FOUND(40400, HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(40500, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    NOT_ACCEPTABLE(40600, HttpStatus.NOT_ACCEPTABLE, "响应类型不支持"),
    CONFLICT(40900, HttpStatus.CONFLICT, "数据冲突"),
    BUSINESS_CONFLICT(40910, HttpStatus.CONFLICT, "业务状态冲突"),
    UNPROCESSABLE_ENTITY(42200, HttpStatus.UNPROCESSABLE_ENTITY, "数据校验失败"),
    PAYLOAD_TOO_LARGE(41300, HttpStatus.PAYLOAD_TOO_LARGE, "上传文件过大"),
    UNSUPPORTED_MEDIA_TYPE(41500, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请求内容类型不支持"),
    TOO_MANY_REQUESTS(42900, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

}
