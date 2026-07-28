package br.org.gam.api.shared.openapi;

import br.org.gam.api.shared.activitylog.RequestCorrelationFilter;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.tags.Tag;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "GAM API"),
        servers = @Server(url = "/api"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    private static final String NON_WHITESPACE_PATTERN =
            "[\\s\\S]*[^\\u0009-\\u000D\\u0020\\u0085\\u00A0\\u1680\\u2000-\\u200A"
                    + "\\u2028\\u2029\\u202F\\u205F\\u3000][\\s\\S]*";

    private static final Set<String> CREATED_OPERATIONS = Set.of(
            "createEvent",
            "createGamLocation",
            "createMember",
            "submitMembershipSolicitation",
            "assignAccountRole",
            "registerAccount",
            "createOratorio",
            "registerOratoriano",
            "createOratorianoFormDraft",
            "createOratorianoFormPrintSnapshot"
    );

    private static final Set<String> NO_CONTENT_OPERATIONS = Set.of(
            "activateMember",
            "deactivateMember",
            "dropAccountRole",
            "removeGamLocation",
            "assignOratorioTeamMember",
            "removeOratorioTeamMember",
            "lockOratorio",
            "finalizeOratorio",
            "reopenOratorio",
            "cancelOratorio",
            "deleteOratorio",
            "deleteOratoriano",
            "restoreOratoriano",
            "deleteOratorianoFormDraft",
            "completeOratorianoForm",
            "revokeOratorianoForm"
    );

    private static final Set<String> PAGED_OPERATIONS = Set.of(
            "searchAccounts",
            "searchEvents",
            "getEventPresences",
            "listGamLocations",
            "searchMembers",
            "getMemberPresences",
            "searchMembershipSolicitations",
            "searchOratorianos",
            "getOratorianoFormHistory"
    );

    @Value("${spring.application.version}")
    private String applicationVersion;

    @Bean
    OpenApiCustomizer completeGeneratedContract() {
        return openApi -> {
            openApi.getInfo().setVersion(applicationVersion);
            openApi.getInfo().setDescription("Backend-owned HTTP contract for the GAM API.");
            openApi.getInfo().setContact(new io.swagger.v3.oas.models.info.Contact().name("GAM API maintainers"));
            openApi.setTags(List.of(
                    new Tag().name("Authentication"),
                    new Tag().name("Membership Solicitations"),
                    new Tag().name("Members"),
                    new Tag().name("Events"),
                    new Tag().name("Presences"),
                    new Tag().name("GamLocations"),
                    new Tag().name("RBAC"),
                    new Tag().name("Accounts"),
                    new Tag().name("Oratorios"),
                    new Tag().name("Oratorianos"),
                    new Tag().name("Oratoriano Forms")
            ));
            Components components = openApi.getComponents() == null ? new Components() : openApi.getComponents();
            openApi.setComponents(components);
            components.addSchemas("ApiErrorDTO", apiErrorSchema());
            requireGamLocationResponseFields(components);
            requireCsrfBootstrapResponseFields(components);
            requireCurrentAccountContextResponseFields(components);
            configureSharedSearchSchemas(components);
            components.getSchemas().remove("Pageable");

            openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperations().forEach(operation -> {
                addOperationMetadata(path, operation);
                if (isPublicPath(path)) {
                    operation.setSecurity(List.of());
                }
                documentSuccessStatus(operation);
                documentModuleMediaTypes(operation);
                documentPagination(operation);
                documentBrowserAuthenticationInputs(operation);
                documentBrowserAuthenticationCookieResponses(operation);
                documentLocationResponseHeader(operation);
                documentErrorResponses(operation);
                documentCurrentAccountContext(operation);
                documentRequestCorrelationResponseHeader(operation);
                addExamples(openApi, operation);
            }));
        };
    }

    private void requireGamLocationResponseFields(Components components) {
        Schema<?> location = components.getSchemas().get("GamLocationRDTO");
        if (location != null) {
            location.setRequired(List.of(
                    "id", "code", "systemManaged", "name", "street", "city", "state",
                    "postalCode", "countryCode", "latitude", "longitude"
            ));
        }
    }

    private void requireCsrfBootstrapResponseFields(Components components) {
        Schema<?> csrfBootstrap = components.getSchemas().get("CsrfBootstrapRDTO");
        if (csrfBootstrap != null) {
            csrfBootstrap.setRequired(List.of("token", "headerName"));
        }
    }

    private void requireCurrentAccountContextResponseFields(Components components) {
        Schema<?> currentAccountContext = components.getSchemas().get("CurrentAccountContextRDTO");
        if (currentAccountContext != null) {
            currentAccountContext.setRequired(List.of("id", "email", "displayName", "roles", "permissions"));

            Schema<?> permissions = currentAccountContext.getProperties().get("permissions");
            permissions.setItems(new StringSchema());
            permissions.setUniqueItems(true);
        }
    }

    private void configureSharedSearchSchemas(Components components) {
        Schema<?> filter = components.getSchemas().get("SpecificationFilterDTO");
        if (filter == null || filter.getProperties() == null) {
            return;
        }

        StringSchema field = new StringSchema();
        field.setMinLength(1);
        field.setPattern(NON_WHITESPACE_PATTERN);
        filter.getProperties().put("field", field);

        StringSchema comparisonMethod = new StringSchema();
        comparisonMethod.setMinLength(1);
        comparisonMethod.setPattern(NON_WHITESPACE_PATTERN);
        comparisonMethod.setEnum(List.of(
                "EQUALS",
                "LIKE",
                "IN",
                "GREATER_THAN_OR_EQUAL",
                "LESS_THAN_OR_EQUAL"
        ));
        filter.getProperties().put("comparisonMethod", comparisonMethod);

        StringSchema scalar = new StringSchema();
        scalar.setMinLength(1);
        scalar.setPattern(NON_WHITESPACE_PATTERN);

        StringSchema inItem = new StringSchema();
        inItem.setMinLength(1);
        inItem.setPattern(NON_WHITESPACE_PATTERN);
        ArraySchema inValues = new ArraySchema();
        inValues.setMinItems(1);
        inValues.setMaxItems(100);
        inValues.setItems(inItem);

        filter.getProperties().put(
                "value",
                new ComposedSchema().oneOf(List.of(scalar, inValues))
        );
    }

    private void addOperationMetadata(String path, io.swagger.v3.oas.models.Operation operation) {
        operation.setTags(List.of(consumerTag(path)));
        if (operation.getSummary() == null || operation.getSummary().isBlank()) {
            operation.setSummary(humanize(operation.getOperationId()));
        }
        if (operation.getDescription() == null || operation.getDescription().isBlank()) {
            operation.setDescription(operationDescription(operation));
        }
    }

    private String operationDescription(io.swagger.v3.oas.models.Operation operation) {
        return switch (operation.getOperationId()) {
            case "createOratorio" ->
                    "Creates the Oratorio occurrence for the supplied local date. "
                            + "Only one active occurrence may use a date; duplicates return "
                            + "ORATORIO_DATE_ALREADY_EXISTS.";
            case "markOratorioMemberPresent" ->
                    "Idempotently marks the Member present. Repeating an existing check returns "
                            + "the existing attendance without creating a duplicate.";
            case "markOratorianoPresent" ->
                    "Idempotently marks the Oratoriano present. Repeating an existing check returns "
                            + "the existing attendance without creating a duplicate.";
            case "completeOratorianoForm" ->
                    "Completes a valid draft only after its complete signed attachment and print snapshot "
                            + "correspondence have been verified. Set overwriteNewerProfileValues to true "
                            + "to explicitly authorize replacement of profile values recorded after signedOn.";
            case "searchAccounts" ->
                    structuredSearchDescription(
                            "id allows EQUALS and IN; value is a canonical UUID string.",
                            "email allows EQUALS and LIKE; value for EQUALS is a complete canonical email, while "
                                    + "LIKE lowercases a literal substring of minimum 3 characters, requires two "
                                    + "before @, and rejects a dot without @.",
                            "displayName allows EQUALS and LIKE; value is trimmed text of 1 to 50 characters, "
                                    + "with case-sensitive EQUALS and case-insensitive literal substring LIKE.",
                            "role allows EQUALS and IN; value is an exact case-sensitive Role name from an "
                                    + "active/current assignment.",
                            "createdAt allows GREATER_THAN_OR_EQUAL and LESS_THAN_OR_EQUAL; "
                                    + "value is a canonical RFC 3339 UTC timestamp ending in Z.",
                            "updatedAt allows GREATER_THAN_OR_EQUAL and LESS_THAN_OR_EQUAL; "
                                    + "value is a canonical RFC 3339 UTC timestamp ending in Z."
                    );
            case "searchEvents" ->
                    structuredSearchDescription(
                            "id allows EQUALS and IN; value is a canonical UUID string.",
                            "title allows EQUALS and LIKE; value is trimmed text of 1 to 255 characters, "
                                    + "with case-sensitive EQUALS and case-insensitive literal substring LIKE.",
                            "description allows LIKE; value is nonblank text after trimming with a maximum of "
                                    + "10,000 characters and case-insensitive literal substring matching.",
                            "gamLocationId allows EQUALS and IN; value is a canonical UUID string.",
                            "requiredPermissionId allows EQUALS and IN; value is a canonical UUID for the nullable "
                                    + "audience Permission; a public Event with no Permission does not match.",
                            "requiredPermissionCode allows EQUALS and IN; value is an exact case-sensitive code "
                                    + "of the active/current nullable audience Permission; a public Event with no "
                                    + "Permission does not match.",
                            "type allows EQUALS and IN; value is an exact uppercase accepted enum: "
                                    + "GENERIC, ORATORIO, or MISSA.",
                            "status allows EQUALS and IN; value is the exact uppercase effective status evaluated "
                                    + "at a single request instant: SCHEDULED, COMPLETED, LOCKED, FINALIZED, "
                                    + "or CANCELLED.",
                            "beginDate allows GREATER_THAN_OR_EQUAL and LESS_THAN_OR_EQUAL; "
                                    + "value is a canonical RFC 3339 UTC timestamp ending in Z.",
                            "endDate allows GREATER_THAN_OR_EQUAL and LESS_THAN_OR_EQUAL; "
                                    + "value is a canonical RFC 3339 UTC timestamp ending in Z."
                    );
            case "searchMembers" ->
                    structuredSearchDescription(
                            "id allows EQUALS and IN; value is a canonical UUID string.",
                            "name allows LIKE; value is a full-name string trimmed at boundaries to collapse every "
                                    + "Unicode whitespace sequence to one space; LIKE is a case-insensitive literal "
                                    + "substring while preserving diacritics and meaningful punctuation.",
                            "birthDate allows EQUALS, GREATER_THAN_OR_EQUAL, and LESS_THAN_OR_EQUAL; "
                                    + "value is an ISO 8601 yyyy-MM-dd calendar date.",
                            "phoneNumber allows EQUALS and LIKE; value for EQUALS is a complete canonical E.164 "
                                    + "phone number, while LIKE removes ordinary phone formatting and requires "
                                    + "a minimum of 4 digits for literal digit-substring matching.",
                            "status allows EQUALS and IN; value is an exact uppercase accepted enum: "
                                    + "ACTIVE or INACTIVE.",
                            "accountId allows EQUALS; value is a canonical UUID string.",
                            "email allows EQUALS and LIKE; value for EQUALS is a complete canonical email, while "
                                    + "LIKE lowercases a literal substring of minimum 3 characters, requires two "
                                    + "before @, and rejects a dot without @.",
                            "role allows EQUALS and IN; value is an exact case-sensitive Role name from an "
                                    + "active/current assignment.",
                            "createdAt allows GREATER_THAN_OR_EQUAL and LESS_THAN_OR_EQUAL; "
                                    + "value is a canonical RFC 3339 UTC timestamp ending in Z.",
                            "updatedAt allows GREATER_THAN_OR_EQUAL and LESS_THAN_OR_EQUAL; "
                                    + "value is a canonical RFC 3339 UTC timestamp ending in Z."
                    );
            case "searchMembershipSolicitations" ->
                    structuredSearchDescription(
                            "id allows EQUALS and IN; value is a canonical UUID string.",
                            "accountId allows EQUALS; value is a canonical UUID string.",
                            "email allows EQUALS and LIKE; value for EQUALS is a complete canonical email, while "
                                    + "LIKE lowercases a literal substring of minimum 3 characters, requires two "
                                    + "before @, and rejects a dot without @.",
                            "name allows LIKE; value is the immutable submitted full-name snapshot that collapses "
                                    + "every Unicode whitespace sequence to one space; LIKE is a case-insensitive "
                                    + "literal substring while preserving diacritics and meaningful punctuation.",
                            "status allows EQUALS and IN; value is an exact uppercase accepted enum: "
                                    + "PENDING, APPROVED, or REJECTED.",
                            "submittedAt allows GREATER_THAN_OR_EQUAL and LESS_THAN_OR_EQUAL; "
                                    + "value is a canonical RFC 3339 UTC timestamp ending in Z.",
                            "decidedAt allows GREATER_THAN_OR_EQUAL and LESS_THAN_OR_EQUAL; "
                                    + "the stored field is nullable; the submitted value is a non-null canonical "
                                    + "RFC 3339 UTC timestamp ending in Z; pending with no decision does not match.",
                            "reviewedByAccountId allows EQUALS; the stored field is nullable; the submitted value is "
                                    + "a non-null canonical UUID; pending with no reviewer does not match."
                    );
            case "searchOratorianos" ->
                    structuredSearchDescription(
                            "id allows EQUALS and IN; value is a canonical UUID string.",
                            "name allows EQUALS and LIKE; value is the human-equivalent complete full-name key with "
                                    + "collapse of Unicode whitespace to one space, compared case-insensitively and "
                                    + "diacritic-insensitively while punctuation remains meaningful; EQUALS requires "
                                    + "the complete key and LIKE performs literal substring matching."
                    );
            default -> "Performs the documented GAM operation: " + operation.getSummary() + ".";
        };
    }

    private String structuredSearchDescription(String... fieldContracts) {
        return "Uses the strict shared filters array. Each filter requires field, value, and comparisonMethod. "
                + "Accepted comparisonMethod values are EQUALS, LIKE, IN, GREATER_THAN_OR_EQUAL, and "
                + "LESS_THAN_OR_EQUAL, subject to each field contract. EQUALS uses one scalar value of the "
                + "field's documented type and parsing rules. LIKE uses one nonblank string. IN uses a JSON "
                + "array of 1 to 100 scalar values under the same equality parsing and normalization rules. "
                + "GREATER_THAN_OR_EQUAL and LESS_THAN_OR_EQUAL each use one scalar value of the field's "
                + "documented ordered type and parsing rules. "
                + String.join(" ", fieldContracts);
    }

    private String consumerTag(String path) {
        if (path.startsWith("/oratorianos/") && path.contains("/forms")) {
            return "Oratoriano Forms";
        }
        if (path.startsWith("/oratorianos")) {
            return "Oratorianos";
        }
        if (path.startsWith("/oratorios")) {
            return "Oratorios";
        }
        if (path.contains("/presences")) {
            return "Presences";
        }
        if (path.startsWith("/auth")) {
            return "Authentication";
        }
        if (path.startsWith("/membership-solicitations")) {
            return "Membership Solicitations";
        }
        if (path.startsWith("/members")) {
            return "Members";
        }
        if (path.startsWith("/events")) {
            return "Events";
        }
        if (path.startsWith("/gam-locations")) {
            return "GamLocations";
        }
        if (path.startsWith("/roles") || path.startsWith("/permissions") || path.contains("/roles")) {
            return "RBAC";
        }
        return "Accounts";
    }

    private String humanize(String operationId) {
        return operationId.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private boolean isPublicPath(String path) {
        return "/auth/register".equals(path)
                || "/auth/login".equals(path)
                || "/auth/refresh".equals(path)
                || "/auth/logout".equals(path)
                || "/auth/csrf".equals(path)
                || "/events/{id}".equals(path);
    }

    private Schema<?> apiErrorSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.addProperty("timestamp", new StringSchema().format("date-time"));
        schema.addProperty("status", new IntegerSchema());
        schema.addProperty("code", new StringSchema());
        schema.addProperty("message", new StringSchema());
        schema.addProperty("details", new MapSchema());
        schema.setRequired(List.of("timestamp", "status", "code", "message", "details"));
        return schema;
    }

    private void documentSuccessStatus(io.swagger.v3.oas.models.Operation operation) {
        String operationId = operation.getOperationId();
        if (CREATED_OPERATIONS.contains(operationId)) {
            moveSuccessResponse(operation, "201", "Created", false);
        } else if (NO_CONTENT_OPERATIONS.contains(operationId)) {
            moveSuccessResponse(operation, "204", "No content", true);
        }
    }

    private void moveSuccessResponse(
            io.swagger.v3.oas.models.Operation operation,
            String status,
            String description,
            boolean noContent
    ) {
        ApiResponse response = operation.getResponses().remove("200");
        if (response == null) {
            response = operation.getResponses().get(status);
        }
        if (response == null) {
            response = new ApiResponse();
        }
        response.setDescription(description);
        if (noContent) {
            response.setContent(null);
        }
        operation.getResponses().put(status, response);
    }

    private void documentPagination(io.swagger.v3.oas.models.Operation operation) {
        if (!PAGED_OPERATIONS.contains(operation.getOperationId())) {
            return;
        }

        if (operation.getParameters() != null) {
            operation.getParameters().removeIf(parameter -> Set.of("pageable", "page", "size", "sort")
                    .contains(parameter.getName()));
        }
        operation.addParametersItem(pageParameter());
        operation.addParametersItem(sizeParameter());
        if (!"getOratorianoFormHistory".equals(operation.getOperationId())) {
            operation.addParametersItem(sortParameter(operation.getOperationId()));
        }
    }

    private void documentModuleMediaTypes(io.swagger.v3.oas.models.Operation operation) {
        if (!"renderOratorianoFormPdf".equals(operation.getOperationId())) {
            return;
        }
        ApiResponse response = operation.getResponses().get("200");
        if (response == null) {
            return;
        }
        Schema<?> binary = new StringSchema().format("binary");
        response.setContent(new Content().addMediaType(
                "application/pdf",
                new MediaType().schema(binary)
        ));
    }

    private void documentBrowserAuthenticationInputs(io.swagger.v3.oas.models.Operation operation) {
        String operationId = operation.getOperationId();
        if (Set.of("login", "refreshAccessToken", "logout").contains(operationId)) {
            operation.addParametersItem(new Parameter()
                    .name("X-XSRF-TOKEN")
                    .in("header")
                    .required(true)
                    .description("CSRF proof obtained from GET /auth/csrf.")
                    .schema(new StringSchema()));
        }
        if ("refreshAccessToken".equals(operationId) || "logout".equals(operationId)) {
            operation.addParametersItem(new Parameter()
                    .name("refreshToken")
                    .in("cookie")
                    .required("refreshAccessToken".equals(operationId))
                    .description("Browser-managed HttpOnly refresh cookie; never enter it in Swagger authorization.")
                    .schema(new StringSchema()));
        }
    }

    private void documentBrowserAuthenticationCookieResponses(io.swagger.v3.oas.models.Operation operation) {
        String description = switch (operation.getOperationId()) {
            case "login" -> "Sets the browser-managed refreshToken cookie to establish the authentication session.";
            case "refreshAccessToken" -> "Sets and rotates the browser-managed refreshToken cookie.";
            case "logout" -> "Expires the browser-managed refreshToken cookie with Max-Age=0.";
            default -> null;
        };
        if (description == null) {
            return;
        }

        ApiResponse successResponse = operation.getResponses().get("200");
        if (successResponse != null) {
            successResponse.addHeaderObject(
                    "Set-Cookie",
                    new Header().description(description).schema(new StringSchema())
            );
        }
    }

    private void documentLocationResponseHeader(io.swagger.v3.oas.models.Operation operation) {
        String resource = switch (operation.getOperationId()) {
            case "createGamLocation" -> "GamLocation";
            case "createOratorio" -> "Oratorio";
            case "registerOratoriano" -> "Oratoriano";
            case "createOratorianoFormDraft" -> "Oratoriano form draft";
            default -> null;
        };
        if (resource == null) {
            return;
        }

        ApiResponse createdResponse = operation.getResponses().get("201");
        if (createdResponse != null) {
            createdResponse.addHeaderObject("Location", new Header()
                    .description("Public API URI of the created " + resource + " resource.")
                    .schema(new StringSchema().format("uri")));
        }
    }

    private void documentRequestCorrelationResponseHeader(io.swagger.v3.oas.models.Operation operation) {
        operation.getResponses().values().forEach(response -> response.addHeaderObject(
                RequestCorrelationFilter.HEADER_NAME,
                new Header()
                        .description("UUID correlating this response with activity entries produced by the request.")
                        .schema(new StringSchema().format("uuid"))
        ));
    }

    private Parameter pageParameter() {
        IntegerSchema schema = new IntegerSchema();
        schema.setDefault(0);
        schema.setMinimum(BigDecimal.ZERO);
        return new Parameter()
                .in("query")
                .name("page")
                .description("Zero-based page index.")
                .schema(schema);
    }

    private Parameter sizeParameter() {
        IntegerSchema schema = new IntegerSchema();
        schema.setDefault(20);
        schema.setMinimum(BigDecimal.ONE);
        schema.setMaximum(BigDecimal.valueOf(100));
        return new Parameter()
                .in("query")
                .name("size")
                .description("Page size, from 1 through 100.")
                .schema(schema);
    }

    private Parameter sortParameter(String operationId) {
        ArraySchema schema = new ArraySchema();
        StringSchema itemSchema = new StringSchema();
        if ("searchOratorianos".equals(operationId)) {
            itemSchema.setEnum(List.of(
                    "oratorioYearAttendances,asc",
                    "oratorioYearAttendances,desc"
            ));
        }
        schema.setItems(itemSchema);
        schema.setDefault(switch (operationId) {
            case "searchEvents" -> List.of("beginDate,asc", "id,asc");
            case "getEventPresences" ->
                    List.of("memberFirstName,asc", "memberSurname,asc");
            case "getMemberPresences" ->
                    List.of("eventBeginDate,desc");
            case "searchOratorianos" -> null;
            default -> List.of("name,asc");
        });
        String description = "Repeat this parameter as field,direction. Allowed fields: "
                + String.join(", ", allowedSortFields(operationId)) + ". Directions: asc, desc.";
        if ("searchEvents".equals(operationId)) {
            description += " Status ordering uses effective status at the request evaluation instant. "
                    + "The default is beginDate ascending, then id ascending.";
        } else if ("getEventPresences".equals(operationId)) {
            description += " The default is memberFirstName ascending, memberSurname ascending, "
                    + "then Presence UUID ascending. Presence UUID ascending is appended to every requested sort.";
        } else if ("getMemberPresences".equals(operationId)) {
            description += " The default is eventBeginDate descending, Event UUID descending, "
                    + "then Presence UUID ascending. Presence UUID ascending is appended to every requested sort.";
        } else if ("searchOratorianos".equals(operationId)) {
            description += " The default is normalized name ascending, then Oratoriano UUID ascending. "
                    + "The oratorioYearAttendances sort in either direction also appends normalized name and UUID "
                    + "tie-breakers.";
        }
        return new Parameter()
                .in("query")
                .name("sort")
                .description(description)
                .style(Parameter.StyleEnum.FORM)
                .explode(true)
                .schema(schema);
    }

    private List<String> allowedSortFields(String operationId) {
        return switch (operationId) {
            case "searchAccounts" -> List.of("email", "displayName", "createdAt");
            case "searchEvents" -> List.of("title", "beginDate", "endDate", "type", "status");
            case "getEventPresences" ->
                    List.of("memberFirstName", "memberSurname", "registeredAt");
            case "getMemberPresences" ->
                    List.of("eventBeginDate", "eventTitle", "registeredAt");
            case "listGamLocations" -> List.of("name", "city", "state", "countryCode");
            case "searchMembers" -> List.of("firstName", "surname", "birthDate", "status");
            case "searchMembershipSolicitations" -> List.of("status", "createdAt", "updatedAt");
            case "searchOratorianos" -> List.of("oratorioYearAttendances");
            default -> List.of();
        };
    }

    private void documentErrorResponses(io.swagger.v3.oas.models.Operation operation) {
        if (isStructuredSearch(operation.getOperationId())) {
            operation.getResponses().put("400", structuredSearchBadRequestResponse());
        } else {
            operation.getResponses().putIfAbsent("400", errorResponse(400, "INVALID_REQUEST", "Invalid request"));
        }
        operation.getResponses().putIfAbsent("401", errorResponse(401, "UNAUTHORIZED", "Authentication is required."));
        if (Set.of("updateGamLocation", "removeGamLocation").contains(operation.getOperationId())) {
            operation.getResponses().put("403", gamLocationMutationForbiddenResponse());
        } else {
            operation.getResponses().putIfAbsent(
                    "403",
                    errorResponse(
                            403,
                            "FORBIDDEN",
                            "The authenticated account is not allowed to perform this operation."
                    )
            );
        }
        operation.getResponses().putIfAbsent("404", errorResponse(404, "NOT_FOUND", "The requested resource was not found."));
        ConflictDocumentation conflict = conflictDocumentation(operation.getOperationId());
        operation.getResponses().putIfAbsent(
                "409",
                errorResponse(
                        409,
                        conflict.exampleCode(),
                        conflict.description(),
                        conflict.exampleDetails()
                )
        );
        if ("listRoles".equals(operation.getOperationId())) {
            operation.getResponses().remove("404");
            operation.getResponses().remove("409");
        }
    }

    private boolean isStructuredSearch(String operationId) {
        return Set.of(
                "searchAccounts",
                "searchEvents",
                "searchMembers",
                "searchMembershipSolicitations",
                "searchOratorianos"
        ).contains(operationId);
    }

    private ApiResponse structuredSearchBadRequestResponse() {
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ApiErrorDTO"));
        mediaType.addExamples(
                "malformedJson",
                new Example().summary("Malformed JSON structure")
                        .value(errorExample(400, "MALFORMED_JSON", "JSON request is malformed."))
        );
        mediaType.addExamples(
                "validationError",
                new Example().summary("Missing, null, or blank required member")
                        .value(errorExample(400, "VALIDATION_ERROR", "Validation failed."))
        );
        mediaType.addExamples(
                "invalidSearchFilter",
                new Example().summary("Invalid filter semantics")
                        .value(errorExample(
                                400,
                                "INVALID_SEARCH_FILTER",
                                "Invalid search filter.",
                                Map.of("filterIndex", 0)
                        ))
        );
        return new ApiResponse()
                .description("Possible codes: MALFORMED_JSON, VALIDATION_ERROR, INVALID_SEARCH_FILTER.")
                .content(new Content().addMediaType("application/json", mediaType));
    }

    private ApiResponse gamLocationMutationForbiddenResponse() {
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ApiErrorDTO"));
        mediaType.addExamples(
                "missingPermission",
                new Example().summary("The authenticated account lacks GAM_LOCATION_MANAGE")
                        .value(errorExample(
                                403,
                                "FORBIDDEN",
                                "The authenticated account is not allowed to perform this operation."
                        ))
        );
        mediaType.addExamples(
                "systemManagedLocation",
                new Example().summary("System-managed GamLocation mutation is forbidden")
                        .value(errorExample(
                                403,
                                "FORBIDDEN_OPERATION",
                                "System-managed GamLocations cannot be changed through product workflows.",
                                Map.of(
                                        "resource",
                                        "GamLocation",
                                        "identifier",
                                        "019f6343-321a-7c90-a096-a551e8f88eb4"
                                )
                        ))
        );
        return new ApiResponse()
                .description("Possible codes: FORBIDDEN, FORBIDDEN_OPERATION.")
                .content(new Content().addMediaType("application/json", mediaType));
    }

    private Map<String, Object> errorExample(int status, String code, String message) {
        return errorExample(status, code, message, Map.of());
    }

    private Map<String, Object> errorExample(
            int status,
            String code,
            String message,
            Map<String, Object> details
    ) {
        return Map.of(
                "timestamp", "2026-07-15T12:00:00Z",
                "status", status,
                "code", code,
                "message", message,
                "details", details
        );
    }

    private ConflictDocumentation conflictDocumentation(String operationId) {
        if ("createOratorio".equals(operationId)) {
            return new ConflictDocumentation(
                    "ORATORIO_DATE_ALREADY_EXISTS",
                    "An active Oratorio occurrence already uses the supplied local date.",
                    Map.of(
                            "resource", "Oratorio",
                            "identifier", "2026-07-25"
                    )
            );
        }
        if ("completeOratorianoForm".equals(operationId)) {
            return new ConflictDocumentation(
                    "ORATORIANO_FORM_PROFILE_OVERWRITE_CHOICE_REQUIRED",
                    "Possible codes: ORATORIANO_FORM_PROFILE_OVERWRITE_CHOICE_REQUIRED, "
                            + "ORATORIANO_FORM_PROFILE_SOURCE_IS_NEWER. Completion requires an explicit "
                            + "authorized overwrite choice when values recorded after the form was signed "
                            + "would be replaced, and rejects a source form that is older than the current "
                            + "form-backed profile source.",
                    Map.of(
                            "resource", "OratorianoForm",
                            "identifier", "019f6343-321a-7c90-a096-a551e8f88eb4"
                    )
            );
        }
        if (Set.of(
                "replaceGenericEvent",
                "lockGenericEvent",
                "finalizeGenericEvent",
                "reopenGenericEvent",
                "cancelGenericEvent"
        ).contains(operationId)) {
            return new ConflictDocumentation(
                    "EVENT_STATUS_TRANSITION_NOT_ALLOWED",
                    "Possible codes: EVENT_STATUS_TRANSITION_NOT_ALLOWED, EVENT_TYPE_NOT_MANAGEABLE. "
                            + "Transition details include eventId, currentStatus, and requestedStatus.",
                    Map.of(
                            "eventId", "019f6343-321a-7c90-a096-a551e8f88eb4",
                            "currentStatus", "SCHEDULED",
                            "requestedStatus", "LOCKED"
                    )
            );
        }
        if ("deleteGenericEvent".equals(operationId)) {
            return new ConflictDocumentation(
                    "EVENT_HAS_PRESENCES",
                    "Possible codes: EVENT_HAS_PRESENCES, EVENT_STATUS_TRANSITION_NOT_ALLOWED, "
                            + "EVENT_TYPE_NOT_MANAGEABLE. Details include eventId, activePresenceCount, "
                            + "currentStatus, and requestedStatus as applicable.",
                    Map.of(
                            "eventId", "019f6343-321a-7c90-a096-a551e8f88eb4",
                            "activePresenceCount", 2
                    )
            );
        }
        if ("registerEventPresence".equals(operationId)) {
            return new ConflictDocumentation(
                    "PRESENCE_ALREADY_REGISTERED",
                    "Possible codes: PRESENCE_ALREADY_REGISTERED, PRESENCE_REGISTRATION_NOT_ALLOWED. "
                            + "Duplicate details include eventId, memberId, and presenceId. Eligibility details "
                            + "include eventId, status, beginDate, and evaluationInstant.",
                    Map.of(
                            "eventId", "019f6343-321a-7c90-a096-a551e8f88eb4",
                            "memberId", "019f6343-321a-7c90-a096-a551e8f88eb5",
                            "presenceId", "019f6343-321a-7c90-a096-a551e8f88eb6"
                    )
            );
        }
        if ("updateEventPresenceObservations".equals(operationId)) {
            return new ConflictDocumentation(
                    "PRESENCE_EDIT_NOT_ALLOWED",
                    "Editing is not allowed while the Event is LOCKED or FINALIZED. "
                            + "Details include eventId, presenceId, and effective status.",
                    Map.of(
                            "eventId", "019f6343-321a-7c90-a096-a551e8f88eb4",
                            "presenceId", "019f6343-321a-7c90-a096-a551e8f88eb6",
                            "status", "LOCKED"
                    )
            );
        }
        if ("removeEventPresence".equals(operationId)) {
            return new ConflictDocumentation(
                    "PRESENCE_REMOVAL_NOT_ALLOWED",
                    "Removal is not allowed while the Event is LOCKED or FINALIZED. "
                            + "Details include eventId, presenceId, and effective status.",
                    Map.of(
                            "eventId", "019f6343-321a-7c90-a096-a551e8f88eb4",
                            "presenceId", "019f6343-321a-7c90-a096-a551e8f88eb6",
                            "status", "LOCKED"
                    )
            );
        }
        if ("createGamLocation".equals(operationId) || "updateGamLocation".equals(operationId)) {
            return new ConflictDocumentation(
                    "GAM_LOCATION_ALREADY_EXISTS",
                    "The request conflicts with an existing GamLocation.",
                    Map.of()
            );
        }
        if ("removeGamLocation".equals(operationId)) {
            return new ConflictDocumentation(
                    "GAM_LOCATION_IN_USE",
                    "The GamLocation has historical Event references.",
                    Map.of(
                            "resource", "GamLocation",
                            "identifier", "019f6343-321a-7c90-a096-a551e8f88eb4",
                            "eventReferenceCount", 2
                    )
            );
        }
        return new ConflictDocumentation(
                "CONFLICT",
                "The request conflicts with the current resource state.",
                Map.of()
        );
    }

    private void documentCurrentAccountContext(io.swagger.v3.oas.models.Operation operation) {
        if (!"getCurrentAccountContext".equals(operation.getOperationId())) {
            return;
        }

        operation.getResponses().keySet().removeIf(status -> !Set.of("200", "401").contains(status));
        io.swagger.v3.oas.models.media.MediaType successJson = operation.getResponses()
                .get("200")
                .getContent()
                .get("application/json");
        successJson.setExample(Map.of(
                "id", "019f6343-321a-7c90-a096-a551e8f88eb4",
                "email", "member@example.test",
                "displayName", "Example Member",
                "roles", List.of(Map.of(
                        "id", "019f6343-321a-7c90-a096-a551e8f88eb5",
                        "name", "MEMBER",
                        "description", "Standard authenticated member access",
                        "systemManaged", true
                )),
                "permissions", List.of("ACCOUNT_GET", "EVENT_SEARCH")
        ));
    }

    private ApiResponse errorResponse(int status, String code, String description) {
        return errorResponse(status, code, description, Map.of());
    }

    private ApiResponse errorResponse(int status, String code, String description, Map<String, Object> details) {
        return new ApiResponse()
                .description(description)
                .content(new io.swagger.v3.oas.models.media.Content().addMediaType(
                        "application/json",
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiErrorDTO"))
                                .example(Map.of(
                                        "timestamp", "2026-07-15T12:00:00Z",
                                        "status", status,
                                        "code", code,
                                        "message", description,
                                        "details", details
                                ))
                ));
    }

    private void addExamples(OpenAPI openApi, io.swagger.v3.oas.models.Operation operation) {
        if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
            operation.getRequestBody().getContent().values().forEach(mediaType -> addExample(openApi, mediaType));
        }

        operation.getResponses().values().forEach(response -> {
            if (response.getContent() != null) {
                response.getContent().values().forEach(mediaType -> addExample(openApi, mediaType));
            }
        });
        addStructuredSearchRequestExample(operation);
        correctGamLocationMutationSuccessExample(operation);
    }

    private void correctGamLocationMutationSuccessExample(
            io.swagger.v3.oas.models.Operation operation
    ) {
        String status = switch (operation.getOperationId()) {
            case "createGamLocation" -> "201";
            case "updateGamLocation" -> "200";
            default -> null;
        };
        if (status == null) {
            return;
        }

        ApiResponse response = operation.getResponses().get(status);
        if (response == null || response.getContent() == null) {
            return;
        }
        for (MediaType mediaType : response.getContent().values()) {
            if (mediaType.getExample() instanceof Map<?, ?> example) {
                mediaType.setExample(ordinaryGamLocationExample(example));
            }
        }
    }

    private void addStructuredSearchRequestExample(io.swagger.v3.oas.models.Operation operation) {
        Map<String, Object> filter = switch (operation.getOperationId()) {
            case "searchAccounts" -> searchFilterExample("displayName", "Synthetic Member", "EQUALS");
            case "searchEvents" -> searchFilterExample("title", "Synthetic event", "LIKE");
            case "searchMembers" -> searchFilterExample("status", "ACTIVE", "EQUALS");
            case "searchMembershipSolicitations" -> searchFilterExample("status", "PENDING", "EQUALS");
            case "searchOratorianos" -> searchFilterExample("name", "Ana Silva", "LIKE");
            default -> null;
        };
        if (filter == null || operation.getRequestBody() == null
                || operation.getRequestBody().getContent() == null) {
            return;
        }

        MediaType json = operation.getRequestBody().getContent().get("application/json");
        if (json != null) {
            json.setExample(Map.of("filters", List.of(filter)));
        }
    }

    private Map<String, Object> searchFilterExample(String field, String value, String comparisonMethod) {
        return Map.of(
                "field", field,
                "value", value,
                "comparisonMethod", comparisonMethod
        );
    }

    private void addExample(OpenAPI openApi, io.swagger.v3.oas.models.media.MediaType mediaType) {
        if (mediaType.getExample() == null && (mediaType.getExamples() == null || mediaType.getExamples().isEmpty())) {
            mediaType.setExample(exampleForSchema(openApi, mediaType.getSchema(), "value", new HashSet<>()));
        }
    }

    private Object exampleForSchema(OpenAPI openApi, Schema<?> schema, String propertyName, Set<String> resolvingReferences) {
        if (schema == null) {
            return Map.of();
        }
        if (schema.get$ref() != null) {
            String reference = schema.get$ref();
            if (!resolvingReferences.add(reference)) {
                return Map.of();
            }
            String schemaName = reference.substring(reference.lastIndexOf('/') + 1);
            Schema<?> referencedSchema = openApi.getComponents().getSchemas().get(schemaName);
            Object example = exampleForSchema(openApi, referencedSchema, propertyName, resolvingReferences);
            resolvingReferences.remove(reference);
            if ("GamLocationRDTO".equals(schemaName) && example instanceof Map<?, ?> gamLocation) {
                return ordinaryGamLocationExample(gamLocation);
            }
            return example;
        }
        if (schema.getExample() != null) {
            return schema.getExample();
        }
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return schema.getEnum().getFirst();
        }
        if (schema instanceof ArraySchema || hasType(schema, "array")) {
            return List.of(exampleForSchema(openApi, schema.getItems(), propertyName, resolvingReferences));
        }
        if (hasType(schema, "object") || schema.getProperties() != null) {
            Map<String, Object> example = new LinkedHashMap<>();
            if (schema.getProperties() != null) {
                schema.getProperties().forEach((name, property) ->
                        example.put(name, exampleForSchema(openApi, property, name, resolvingReferences)));
            }
            return example;
        }
        if (schema instanceof IntegerSchema || hasType(schema, "integer")) {
            return schema.getMinimum() == null ? 1 : schema.getMinimum().intValue();
        }
        if (schema instanceof NumberSchema || hasType(schema, "number")) {
            return schema.getMinimum() == null ? 1.0 : schema.getMinimum();
        }
        if (schema instanceof BooleanSchema || hasType(schema, "boolean")) {
            return true;
        }
        return stringExample(schema, propertyName);
    }

    private Map<String, Object> ordinaryGamLocationExample(Map<?, ?> gamLocation) {
        Map<String, Object> ordinaryLocation = new LinkedHashMap<>();
        gamLocation.forEach((key, value) -> ordinaryLocation.put(String.valueOf(key), value));
        ordinaryLocation.put("code", null);
        ordinaryLocation.put("systemManaged", false);
        return ordinaryLocation;
    }

    private boolean hasType(Schema<?> schema, String type) {
        return type.equals(schema.getType()) || (schema.getTypes() != null && schema.getTypes().contains(type));
    }

    private String stringExample(Schema<?> schema, String propertyName) {
        String normalizedName = propertyName.toLowerCase();
        if ("byte".equals(schema.getFormat())) {
            return "U3ludGhldGljIEdBTSBiaW5hcnkgY29udGVudA==";
        }
        if ("date-time".equals(schema.getFormat()) || normalizedName.endsWith("at") || "timestamp".equals(normalizedName)) {
            return "2026-07-15T12:00:00Z";
        }
        if ("date".equals(schema.getFormat()) || normalizedName.endsWith("date")) {
            return "2026-07-15";
        }
        if ("email".equals(schema.getFormat()) || normalizedName.contains("email")) {
            return "developer@example.test";
        }
        if ("uuid".equals(schema.getFormat()) || normalizedName.endsWith("id")) {
            return "019f6343-321a-7c90-a096-a551e8f88eb4";
        }
        if (normalizedName.contains("countrycode")) {
            return "BR";
        }
        if (normalizedName.contains("password")) {
            return "Synthetic-password-123";
        }
        return "Synthetic GAM value";
    }

    private record ConflictDocumentation(
            String exampleCode,
            String description,
            Map<String, Object> exampleDetails
    ) {
    }
}
