package br.org.gam.api.shared.openapi;

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
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
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

    private static final Set<String> CREATED_OPERATIONS = Set.of(
            "createEvent",
            "createLocation",
            "createMember",
            "submitMembershipSolicitation",
            "assignAccountRole",
            "registerAccount"
    );

    private static final Set<String> NO_CONTENT_OPERATIONS = Set.of(
            "activateMember",
            "deactivateMember",
            "dropAccountRole"
    );

    private static final Set<String> PAGED_OPERATIONS = Set.of(
            "searchAccounts",
            "searchEvents",
            "getEventPresences",
            "getLocations",
            "searchMembers",
            "getMemberPresences",
            "searchMembershipSolicitations"
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
                    new Tag().name("Locations"),
                    new Tag().name("RBAC"),
                    new Tag().name("Accounts")
            ));
            Components components = openApi.getComponents() == null ? new Components() : openApi.getComponents();
            openApi.setComponents(components);
            components.addSchemas("ApiErrorDTO", apiErrorSchema());
            requireLocationResponseFields(components);
            requireCsrfBootstrapResponseFields(components);
            requireCurrentAccountContextResponseFields(components);
            components.getSchemas().remove("Pageable");

            openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperations().forEach(operation -> {
                addOperationMetadata(path, operation);
                if (isPublicPath(path)) {
                    operation.setSecurity(List.of());
                }
                documentSuccessStatus(operation);
                documentPagination(operation);
                documentBrowserAuthenticationInputs(operation);
                documentBrowserAuthenticationCookieResponses(operation);
                documentErrorResponses(operation);
                documentCurrentAccountContext(operation);
                addExamples(openApi, operation);
            }));
        };
    }

    private void requireLocationResponseFields(Components components) {
        Schema<?> location = components.getSchemas().get("LocationRDTO");
        if (location != null) {
            location.setRequired(List.of("id", "name", "city", "state", "countryCode"));
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

    private void addOperationMetadata(String path, io.swagger.v3.oas.models.Operation operation) {
        operation.setTags(List.of(consumerTag(path)));
        if (operation.getSummary() == null || operation.getSummary().isBlank()) {
            operation.setSummary(humanize(operation.getOperationId()));
        }
        if (operation.getDescription() == null || operation.getDescription().isBlank()) {
            operation.setDescription("Performs the documented GAM operation: " + operation.getSummary() + ".");
        }
    }

    private String consumerTag(String path) {
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
        if (path.startsWith("/locations")) {
            return "Locations";
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
        operation.addParametersItem(sortParameter(operation.getOperationId()));
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
        schema.setItems(new StringSchema());
        schema.setDefault(List.of("name,asc"));
        return new Parameter()
                .in("query")
                .name("sort")
                .description("Repeat this parameter as field,direction. Allowed fields: "
                        + String.join(", ", allowedSortFields(operationId)) + ". Directions: asc, desc.")
                .style(Parameter.StyleEnum.FORM)
                .explode(true)
                .schema(schema);
    }

    private List<String> allowedSortFields(String operationId) {
        return switch (operationId) {
            case "searchAccounts" -> List.of("email", "displayName", "createdAt");
            case "searchEvents" -> List.of("title", "beginDate", "endDate", "type", "status");
            case "getEventPresences", "getMemberPresences" -> List.of("createdAt", "updatedAt");
            case "getLocations" -> List.of("name", "city", "state", "countryCode");
            case "searchMembers" -> List.of("firstName", "surname", "birthDate", "status");
            case "searchMembershipSolicitations" -> List.of("status", "createdAt", "updatedAt");
            default -> List.of();
        };
    }

    private void documentErrorResponses(io.swagger.v3.oas.models.Operation operation) {
        operation.getResponses().putIfAbsent("400", errorResponse(400, "INVALID_REQUEST", "Invalid request"));
        operation.getResponses().putIfAbsent("401", errorResponse(401, "UNAUTHORIZED", "Authentication is required."));
        operation.getResponses().putIfAbsent("403", errorResponse(403, "FORBIDDEN", "The authenticated account is not allowed to perform this operation."));
        operation.getResponses().putIfAbsent("404", errorResponse(404, "NOT_FOUND", "The requested resource was not found."));
        operation.getResponses().putIfAbsent("409", errorResponse(409, "CONFLICT", "The request conflicts with the current resource state."));
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
                                        "details", Map.of()
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
    }

    private void addExample(OpenAPI openApi, io.swagger.v3.oas.models.media.MediaType mediaType) {
        if (mediaType.getExample() == null && (mediaType.getExamples() == null || mediaType.getExamples().isEmpty())) {
            mediaType.setExample(exampleForSchema(openApi, mediaType.getSchema(), "value", new HashSet<>()));
        }
    }

    @SuppressWarnings("unchecked")
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
            Map<String, Schema> properties = schema.getProperties();
            if (properties != null) {
                properties.forEach((name, property) ->
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

    private boolean hasType(Schema<?> schema, String type) {
        return type.equals(schema.getType()) || (schema.getTypes() != null && schema.getTypes().contains(type));
    }

    private String stringExample(Schema<?> schema, String propertyName) {
        String normalizedName = propertyName.toLowerCase();
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
}
