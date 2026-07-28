package club.muimi.backend.exception;

import club.muimi.backend.common.api.ErrorCode;

public class TooManyRequestsException extends BusinessException {

    public TooManyRequestsException(String message) {
        super(ErrorCode.TOO_MANY_REQUESTS, message);
    }
}
