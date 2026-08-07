package br.org.gam.api.shared.exception;

import br.org.gam.api.security.application.InvalidTokenFormatException;
import br.org.gam.api.member.application.MemberPreconditionException;
import br.org.gam.api.security.application.RequestSecurityRejectedException;
import br.org.gam.api.security.application.RefreshTokenExpiredException;
import br.org.gam.api.security.application.TokenNotFoundException;
import br.org.gam.api.shared.domain.InvalidEmailException;
import br.org.gam.api.shared.specification.InvalidSearchFilterException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import br.org.gam.api.shared.phonenumber.InvalidPhoneNumberException;
import com.fasterxml.jackson.databind.JsonMappingException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.hibernate.id.IdentifierGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
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
        Map<ViolationKey, Map<String, Object>> violations = new TreeMap<>(
                Comparator.comparing(ViolationKey::location)
                        .thenComparing(ViolationKey::field)
                        .thenComparing(ViolationKey::code)
        );

        ex.getBindingResult().getFieldErrors().forEach(fieldError -> {
            String violationCode = validationCode(fieldError.getCode(), fieldError.getRejectedValue());
            addViolation(
                    violations,
                    new ViolationKey("body", bodyField(fieldError.getField()), violationCode)
            );
        });
        ex.getBindingResult().getGlobalErrors().forEach(error -> {
            String violationCode = validationCode(error.getCode(), Boolean.TRUE);
            addViolation(violations, new ViolationKey("body", "$", violationCode));
        });

        ApiErrorDTO errorDTO = new ApiErrorDTO(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation error: the request contains invalid input.",
                Map.of("violations", List.copyOf(violations.values()))
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorDTO);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorDTO> illegalArgumentHandler(IllegalArgumentException e) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ApiErrorDTO> requestValidationHandler(RequestValidationException exception) {
        return singleValidationViolationResponse(
                exception.getLocation(),
                exception.getField(),
                exception.getViolationCode()
        );
    }

    @ExceptionHandler(RequestParameterTypeException.class)
    public ResponseEntity<ApiErrorDTO> requestParameterTypeHandler(RequestParameterTypeException exception) {
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER_TYPE",
                "A request parameter has an incompatible type.",
                Map.of(
                        "location", exception.getLocation(),
                        "field", exception.getField(),
                        "expectedType", exception.getExpectedType()
                )
        );
    }

    private ResponseEntity<ApiErrorDTO> singleValidationViolationResponse(
            String location,
            String field,
            String violationCode
    ) {
        Map<ViolationKey, Map<String, Object>> violations = new TreeMap<>(
                Comparator.comparing(ViolationKey::location)
                        .thenComparing(ViolationKey::field)
                        .thenComparing(ViolationKey::code)
        );
        addViolation(
                violations,
                new ViolationKey(
                        location,
                        field,
                        violationCode
                )
        );
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation error: the request contains invalid input.",
                Map.of("violations", List.copyOf(violations.values()))
        );
    }

    @ExceptionHandler(InvalidPhoneNumberException.class)
    public ResponseEntity<ApiErrorDTO> invalidPhoneNumberHandler(InvalidPhoneNumberException e) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_PHONE_NUMBER", e.getMessage());
    }

    @ExceptionHandler(InvalidSearchFilterException.class)
    public ResponseEntity<ApiErrorDTO> invalidSearchFilterHandler(InvalidSearchFilterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ApiErrorDTO(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_SEARCH_FILTER",
                        e.getMessage(),
                        e.getDetails()
                )
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorDTO> typeMismatchHandler(
            MethodArgumentTypeMismatchException e,
            HttpServletRequest request
    ) {
        String field = e.getName();
        Map<String, Object> details = Map.of(
                "location", parameterLocation(e, request),
                "field", field,
                "expectedType", publicTransportType(e.getRequiredType())
        );
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER_TYPE",
                "A request parameter has an incompatible type.",
                details
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorDTO> dataIntegrityViolationHandler(DataIntegrityViolationException e) {
        log.warn("Data integrity violation detected.", e);
        if (isPresenceUniquenessConflict(e)) {
            return buildApplicationErrorResponse(
                    HttpStatus.CONFLICT,
                    ConflictException.resource(
                            "PRESENCE_ALREADY_REGISTERED",
                            "Presence",
                            null,
                            "Presence already registered for the Event and Member."
                    )
            );
        }
        if (isConcurrentUniquenessConflict(e)) {
            return buildApplicationErrorResponse(
                    HttpStatus.CONFLICT,
                    ConflictException.reason("The request conflicts with an existing or concurrent resource.")
            );
        }

        // Generic message to avoid exposing DB schema details
        String message = "Data integrity error. The request may violate a database constraint.";
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "DATA_INTEGRITY_ERROR", message);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        Throwable cause = jsonCause(ex);
        if (hasCause(ex, InvalidPhoneNumberException.class)
                || hasCause(ex, InvalidEmailException.class)) {
            String field = jsonPointerFromCauseChain(ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(singleValidationViolationResponse(
                            "body",
                            field == null ? "$" : field,
                            "FORMAT"
                    ).getBody());
        }
        String reason = "SYNTAX_ERROR";
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("location", "body");

        if (cause instanceof UnrecognizedPropertyException) {
            details.put("reason", "UNKNOWN_FIELD");
        } else if (cause instanceof MismatchedInputException mismatchedInput) {
            details.put("reason", "TYPE_MISMATCH");
            String field = jsonPointer(mismatchedInput.getPath());
            if (field != null) {
                details.put("field", field);
            }
        } else if (!(cause instanceof JsonParseException)) {
            details.put("reason", "TYPE_MISMATCH");
        }

        ApiErrorDTO errorDTO = new ApiErrorDTO(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON",
                "The JSON request body is malformed.",
                details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorDTO);
    }

    private Throwable jsonCause(Throwable exception) {
        Throwable current = exception;
        Throwable candidate = exception;
        while (current != null) {
            if (current instanceof UnrecognizedPropertyException
                    || current instanceof MismatchedInputException
                    || current instanceof JsonParseException) {
                candidate = current;
            }
            current = current.getCause();
        }
        return candidate;
    }

    private String jsonPointerFromCauseChain(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof JsonMappingException mappingException) {
                String pointer = jsonPointer(mappingException.getPath());
                if (pointer != null) {
                    return pointer;
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> causeType) {
        Throwable current = exception;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String jsonPointer(List<JsonMappingException.Reference> references) {
        if (references.isEmpty()) {
            return null;
        }
        StringBuilder pointer = new StringBuilder();
        for (JsonMappingException.Reference reference : references) {
            if (reference.getFieldName() != null) {
                pointer.append('/').append(escapeJsonPointer(reference.getFieldName()));
            } else if (reference.getIndex() >= 0) {
                pointer.append('/').append(reference.getIndex());
            }
        }
        return pointer.isEmpty() ? null : pointer.toString();
    }

    // =====================================================================================
    // == 401 UNAUTHORIZED - Authentication errors (Missing credentials)
    // =====================================================================================

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorDTO> authenticationHandler(
            AuthenticationException ignored,
            HttpServletRequest request
    ) {
        if ("/auth/login".equals(request.getServletPath())) {
            return buildErrorResponse(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "The supplied credentials are invalid."
            );
        }

        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Authentication failed. Bearer authentication is required.",
                Map.of(),
                true
        );
    }

    // =====================================================================================
    // == 403 FORBIDDEN - Authorization errors (Missing permissions)
    // =====================================================================================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorDTO> accessDeniedHandler(AccessDeniedException ignored) {
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Access denied. You do not have permission for this action."
        );
    }

    @ExceptionHandler({
            CsrfException.class,
            RequestSecurityRejectedException.class
    })
    public ResponseEntity<ApiErrorDTO> requestSecurityRejectedHandler(AccessDeniedException ignored) {
        return buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "REQUEST_SECURITY_REJECTED",
                "Required request security proof was rejected."
        );
    }

    // =====================================================================================
    // == 404 NOT FOUND - Resource not found
    // =====================================================================================

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorDTO> resourceNotFoundHandler(NotFoundException e) {
        Map<String, Object> details = new LinkedHashMap<>(e.getDetails());
        details.put("resource", e.getResource());
        details.put("identifier", e.getIdentifier());
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                e.getCode(),
                "%s not found with the supplied identifier.".formatted(e.getResource()),
                details
        );
    }

    // =====================================================================================
    // == 409 CONFLICT - State conflict (e.g., duplicate resource)
    // =====================================================================================

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorDTO> resourceConflictHandler(ConflictException e) {
        return buildApplicationErrorResponse(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(MemberPreconditionException.class)
    public ResponseEntity<ApiErrorDTO> memberPreconditionHandler(MemberPreconditionException exception) {
        HttpStatus status = switch (exception.getKind()) {
            case REQUIRED -> HttpStatus.PRECONDITION_REQUIRED;
            case FAILED -> HttpStatus.PRECONDITION_FAILED;
            case MALFORMED -> HttpStatus.BAD_REQUEST;
        };
        String code = switch (exception.getKind()) {
            case REQUIRED -> "PRECONDITION_REQUIRED";
            case FAILED -> "PRECONDITION_FAILED";
            case MALFORMED -> "INVALID_PRECONDITION";
        };
        return buildErrorResponse(status, code, exception.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorDTO> optimisticLockingHandler(ObjectOptimisticLockingFailureException ignored) {
        return buildApplicationErrorResponse(
                HttpStatus.CONFLICT,
                ConflictException.reason("The resource was changed by a concurrent request.")
        );
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ApiErrorDTO> forbiddenOperationHandler(ForbiddenOperationException e) {
        return buildApplicationErrorResponse(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(InvalidCommandException.class)
    public ResponseEntity<ApiErrorDTO> invalidCommandHandler(InvalidCommandException e) {
        return buildApplicationErrorResponse(HttpStatus.BAD_REQUEST, e);
    }


    // =====================================================================================
    // == AUTHENTICATION / TOKEN ERRORS
    // =====================================================================================

    @ExceptionHandler({
            TokenNotFoundException.class,
            InvalidTokenFormatException.class,
            RefreshTokenExpiredException.class
    })
    public ResponseEntity<ApiErrorDTO> handleTokenExceptions(RuntimeException e) {
        return buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN",
                "The refresh token is invalid. Please sign in again."
        );
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
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "ID_GENERATION_FAILED", message);
        }

        log.error("Unhandled JpaSystemException captured: ", e);
        String message = "Unexpected persistence layer error.";
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "PERSISTENCE_ERROR", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDTO> genericExceptionHandler(Exception e) {
        log.error("Generic unhandled error was captured by the handler: ", e);
        String message = "Unexpected internal server error.";
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", message);
    }

    // =====================================================================================
    // == Helper Method
    // =====================================================================================

    private ResponseEntity<ApiErrorDTO> buildApplicationErrorResponse(HttpStatus status, ApplicationException exception) {
        return ResponseEntity
                .status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorDTO.from(status, exception));
    }

    private ResponseEntity<ApiErrorDTO> buildErrorResponse(HttpStatus status, String code, String message) {
        return buildErrorResponse(status, code, message, Map.of());
    }

    private ResponseEntity<ApiErrorDTO> buildErrorResponse(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details
    ) {
        return buildErrorResponse(status, code, message, details, false);
    }

    private ResponseEntity<ApiErrorDTO> buildErrorResponse(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details,
            boolean bearerChallenge
    ) {
        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON);
        if (bearerChallenge) {
            response.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return response.body(new ApiErrorDTO(status, code, message, details));
    }

    private void addViolation(
            Map<ViolationKey, Map<String, Object>> violations,
            ViolationKey key
    ) {
        violations.computeIfAbsent(key, ignored -> {
            Map<String, Object> violation = new LinkedHashMap<>();
            violation.put("location", key.location());
            violation.put("field", key.field());
            violation.put("code", key.code());
            violation.put("message", validationMessage(key.code()));
            return violation;
        });
    }

    private String bodyField(String javaPath) {
        String indexedPath = javaPath.replaceAll("\\[(\\d+)]", ".$1");
        StringBuilder pointer = new StringBuilder();
        for (String segment : indexedPath.split("\\.")) {
            if (!segment.isBlank()) {
                pointer.append('/').append(escapeJsonPointer(segment));
            }
        }
        return pointer.isEmpty() ? "$" : pointer.toString();
    }

    private String escapeJsonPointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private String validationCode(String springCode, Object rejectedValue) {
        if ("Null".equals(springCode)) {
            return "INVALID_VALUE";
        }
        if (rejectedValue == null
                || rejectedValue instanceof JsonNode jsonNode && jsonNode.isNull()) {
            return "REQUIRED";
        }
        if (springCode == null) {
            return "INVALID_VALUE";
        }
        return switch (springCode) {
            case "NotNull" -> "REQUIRED";
            case "NotBlank" -> "NOT_BLANK";
            case "NotEmpty", "Size" -> "SIZE";
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive",
                    "PositiveOrZero", "Negative", "NegativeOrZero",
                    "Past", "PastOrPresent", "Future", "FutureOrPresent" -> "RANGE";
            case "Email", "Pattern" -> "FORMAT";
            case "AssertTrue", "AssertFalse" -> "RELATION";
            default -> "INVALID_VALUE";
        };
    }

    private String validationMessage(String code) {
        return switch (code) {
            case "REQUIRED" -> "is required";
            case "NOT_BLANK" -> "must not be blank";
            case "SIZE" -> "has an invalid size";
            case "RANGE" -> "is outside the allowed range";
            case "FORMAT" -> "has an invalid format";
            case "ALLOWED_VALUE" -> "is not an allowed value";
            case "RELATION" -> "is inconsistent with another value";
            default -> "is invalid";
        };
    }

    private String parameterLocation(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        if (exception.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return "path";
        }
        if (exception.getParameter().hasParameterAnnotation(RequestHeader.class)) {
            return "header";
        }
        if (exception.getParameter().hasParameterAnnotation(CookieValue.class)) {
            return "cookie";
        }
        if (exception.getParameter().hasParameterAnnotation(RequestParam.class)) {
            return "query";
        }

        Object variables = request.getAttribute(
                org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
        );
        if (variables instanceof Map<?, ?> pathVariables && pathVariables.containsKey(exception.getName())) {
            return "path";
        }
        if (request.getParameterMap().containsKey(exception.getName())) {
            return "query";
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (exception.getName().equals(cookie.getName())) {
                    return "cookie";
                }
            }
        }
        return "header";
    }

    private String publicTransportType(Class<?> requiredType) {
        if (requiredType == null) {
            return "ENUM";
        }
        if (UUID.class.isAssignableFrom(requiredType)) {
            return "UUID";
        }
        if (requiredType == byte.class || requiredType == short.class
                || requiredType == int.class || requiredType == long.class
                || Number.class.isAssignableFrom(requiredType)
                && !BigDecimal.class.isAssignableFrom(requiredType)
                && requiredType != Float.class
                && requiredType != Double.class) {
            return "INTEGER";
        }
        if (requiredType == float.class || requiredType == double.class
                || BigDecimal.class.isAssignableFrom(requiredType)
                || requiredType == Float.class || requiredType == Double.class) {
            return "DECIMAL";
        }
        if (requiredType == boolean.class || requiredType == Boolean.class) {
            return "BOOLEAN";
        }
        if (LocalDate.class.isAssignableFrom(requiredType)) {
            return "DATE";
        }
        if (Instant.class.isAssignableFrom(requiredType)
                || LocalDateTime.class.isAssignableFrom(requiredType)
                || OffsetDateTime.class.isAssignableFrom(requiredType)
                || ZonedDateTime.class.isAssignableFrom(requiredType)) {
            return "DATE_TIME";
        }
        return "ENUM";
    }

    private record ViolationKey(String location, String field, String code) {
    }

    private boolean isConcurrentUniquenessConflict(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolation
                    && isConcurrentUniquenessConstraint(constraintViolation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }

        return exception.getMessage() != null
                && (exception.getMessage().contains("idx_account_role_not_deleted")
                || exception.getMessage().contains("idx_members_account_id")
                || exception.getMessage().contains("idx_membership_solicitations_one_pending"));
    }

    private boolean isPresenceUniquenessConflict(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException constraintViolation
                    && "idx_presence_not_deleted".equals(constraintViolation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return exception.getMessage() != null
                && exception.getMessage().contains("idx_presence_not_deleted");
    }

    private boolean isConcurrentUniquenessConstraint(String constraintName) {
        return "idx_account_role_not_deleted".equals(constraintName)
                || "idx_members_account_id".equals(constraintName)
                || "idx_membership_solicitations_one_pending".equals(constraintName);
    }
}
