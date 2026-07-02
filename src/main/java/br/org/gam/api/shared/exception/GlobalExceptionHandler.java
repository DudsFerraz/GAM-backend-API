package br.org.gam.api.shared.exception;

import br.org.gam.api.account.application.AccountConflictException;
import br.org.gam.api.account.application.AccountNotFoundException;
import br.org.gam.api.event.application.EventNotFoundException;
import br.org.gam.api.location.application.LocationNotFoundException;
import br.org.gam.api.member.application.MemberAccountConflictException;
import br.org.gam.api.member.application.MemberNotFoundException;
import br.org.gam.api.presence.application.PresenceConflictException;
import br.org.gam.api.presence.application.PresenceNotFoundException;
import br.org.gam.api.rbac.AccountRole.application.AccountRoleNotFoundException;
import br.org.gam.api.rbac.AccountRole.domain.AccountRole;
import br.org.gam.api.rbac.Permission.application.PermissionNotFoundException;
import br.org.gam.api.rbac.Permission.domain.Permission;
import br.org.gam.api.rbac.Role.application.RoleNotFoundException;
import br.org.gam.api.rbac.Role.domain.Role;
import br.org.gam.api.rbac.RolePermission.application.RolePermissionNotFoundException;
import br.org.gam.api.rbac.RolePermission.domain.RolePermission;
import br.org.gam.api.security.application.InvalidTokenFormatException;
import br.org.gam.api.security.application.RefreshTokenExpiredException;
import br.org.gam.api.security.application.TokenNotFoundException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.id.IdentifierGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // =====================================================================================
    // == 400 BAD REQUEST - Input, validation, format errors...
    // =====================================================================================

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {

        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> String.format("'%s': %s", fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        String message = "Validation error: " + String.join(", ", errors);

        ApiErrorDTO errorDTO = new ApiErrorDTO(HttpStatus.BAD_REQUEST, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDTO);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorDTO> illegalArgumentHandler(IllegalArgumentException e) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidPhoneNumberException.class)
    public ResponseEntity<ApiErrorDTO> invalidPhoneNumberHandler(InvalidPhoneNumberException e) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorDTO> typeMismatchHandler(MethodArgumentTypeMismatchException e) {
        String message = String.format("The URL parameter '%s' received an invalid value type.", e.getName());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorDTO> dataIntegrityViolationHandler(DataIntegrityViolationException e) {
        log.warn("Data integrity violation detected.", e);
        // Generic message to avoid exposing DB schema details
        String message = "Data integrity error. The request may violate a database constraint.";
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {

        String friendlyMessage = "JSON Request malformed or eligible.";
        Throwable cause = ex.getCause();

        try {
            if (cause instanceof UnrecognizedPropertyException upx) {
                friendlyMessage = String.format("Unrecognizable field on request: '%s'", upx.getPropertyName());
            }
            else if (cause instanceof MismatchedInputException mie) {

                String fieldName = mie.getPath().stream()
                        .map(JsonMappingException.Reference::getFieldName)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.joining("."));

                String expectedType = mie.getTargetType().getSimpleName();

                friendlyMessage = String.format(
                        "Invalid format for field '%s'. Expected a value compatible with '%s'.",
                        fieldName,
                        expectedType
                );
            }
        } catch (Exception e) {
            log.warn("Unable to generate user friendly message for HttpMessageNotReadableException", e);
        }

        ApiErrorDTO errorDTO = new ApiErrorDTO(HttpStatus.BAD_REQUEST, friendlyMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDTO);
    }

    // =====================================================================================
    // == 401 UNAUTHORIZED - Authentication errors (Missing credentials)
    // =====================================================================================

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorDTO> authenticationHandler(AuthenticationException ignored) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication failed. Please check your credentials.");
    }

    // =====================================================================================
    // == 403 FORBIDDEN - Authorization errors (Missing permissions)
    // =====================================================================================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorDTO> accessDeniedHandler(AccessDeniedException ignored) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access denied. You do not have permission for this action.");
    }

    // =====================================================================================
    // == 404 NOT FOUND - Resource not found
    // =====================================================================================

    @ExceptionHandler({
            AccountNotFoundException.class,
            MemberNotFoundException.class,
            EventNotFoundException.class,
            LocationNotFoundException.class,
            PresenceNotFoundException.class,
            RoleNotFoundException.class,
            AccountRoleNotFoundException.class,
            PermissionNotFoundException.class,
            RolePermissionNotFoundException.class,
    })
    public ResponseEntity<ApiErrorDTO> resourceNotFoundHandler(RuntimeException e) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // =====================================================================================
    // == 409 CONFLICT - State conflict (e.g., duplicate resource)
    // =====================================================================================

    @ExceptionHandler({
            AccountConflictException.class,
            MemberAccountConflictException.class,
            PresenceConflictException.class,
    })
    public ResponseEntity<ApiErrorDTO> resourceConflictHandler(RuntimeException e) {
        return buildErrorResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    //fazer conflict generico igual o not found


    // =====================================================================================
    // == AUTHENTICATION / TOKEN ERRORS
    // =====================================================================================

    @ExceptionHandler({
            TokenNotFoundException.class,
            InvalidTokenFormatException.class
    })
    public ResponseEntity<ApiErrorDTO> handleTokenExceptions(RuntimeException e) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Invalid or expired refresh token. Please sign in again.");
    }

    @ExceptionHandler(RefreshTokenExpiredException.class)
    public ResponseEntity<ApiErrorDTO> handleTokenExpired(RefreshTokenExpiredException e) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    // =====================================================================================
    // == 500 INTERNAL SERVER ERROR - Generic error
    // =====================================================================================

    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ApiErrorDTO> jpaSystemExceptionHandler(JpaSystemException e) {
        Throwable cause = e.getMostSpecificCause();

        if (cause instanceof IdentifierGenerationException) {
            log.error("FATAL: An entity without ID tried to be persisted. Verify @PrePersist or @GeneratedValue.", e);

            String message = String.format(
                    "Internal Server Error: ID generation failed. %s",
                    cause.getMessage()
            );
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message);
        }

        log.error("Unhandled JpaSystemException captured: ", e);
        String message = "Unexpected persistence layer error.";
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDTO> genericExceptionHandler(Exception e) {
        log.error("Generic unhandled error was captured by the handler: ", e);
        String message = "Unexpected internal server error.";
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    // =====================================================================================
    // == Helper Method
    // =====================================================================================

    private ResponseEntity<ApiErrorDTO> buildErrorResponse(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(new ApiErrorDTO(status, message));
    }
}