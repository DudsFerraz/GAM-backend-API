package br.org.gam.api.shared.exception;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

public record ApiErrorDTO(
        String timestamp,
        int status,
        String error,
        String code,
        String message,
        Map<String, Object> details
) {
    public ApiErrorDTO(HttpStatus status, String message) {
        this(status, status.name(), message, Map.of());
    }

    public ApiErrorDTO(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiErrorDTO(HttpStatus status, String code, String message, Map<String, Object> details) {
        this(
                OffsetDateTime.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                details == null ? Map.of() : Map.copyOf(details)
        );
    }

    public static ApiErrorDTO from(HttpStatus status, ApplicationException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (exception.getResource() != null) {
            details.put("resource", exception.getResource());
        }
        if (exception.getIdentifier() != null) {
            details.put("identifier", exception.getIdentifier());
        }

        return new ApiErrorDTO(status, exception.getCode(), exception.getMessage(), details);
    }
}
