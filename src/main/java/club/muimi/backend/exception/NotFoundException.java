package club.muimi.backend.exception;

import club.muimi.backend.common.api.ErrorCode;

public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
