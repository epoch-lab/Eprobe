package club.muimi.backend.exception;

import club.muimi.backend.common.api.ErrorCode;

public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
