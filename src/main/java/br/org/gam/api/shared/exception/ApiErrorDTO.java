package br.org.gam.api.shared.exception;

import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;

public record ApiErrorDTO(
        int status,
        String error,
        String message,
        String timestamp
) {
    public ApiErrorDTO(HttpStatus status, String message) {
        this(
                status.value(),
                status.getReasonPhrase(),
                message,
                OffsetDateTime.now().toString()
        );
    }
}
