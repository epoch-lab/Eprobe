package club.muimi.backend.common.api;

public record FieldValidationError(
        String field,
        String message
) {
}
