package club.muimi.backend.common.api;

import java.util.List;

public record ValidationErrorBody(
        List<FieldValidationError> fieldErrors
) {
}
