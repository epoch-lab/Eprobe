package club.muimi.backend.exception;

import club.muimi.backend.common.api.ErrorCode;

public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(ErrorCode.UNPROCESSABLE_ENTITY, message);
    }
}
