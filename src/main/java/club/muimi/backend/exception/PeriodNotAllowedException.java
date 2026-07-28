package club.muimi.backend.exception;

import club.muimi.backend.common.api.ErrorCode;

public class PeriodNotAllowedException extends BusinessException {

    public PeriodNotAllowedException(String message) {
        super(ErrorCode.PERIOD_NOT_ALLOWED, message);
    }
}
