package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@DisplayName("API - OpenAPI shared schemas")
class OpenApiSharedSchemasApiIT extends AbstractOpenApiDocumentationApiIT {

    @Test
    @DisplayName("REQ-OPENAPI-006 - generated ApiErrorDTO schema -> exact five-field envelope without error reason phrase")
    void apiErrorSchemaShouldExposeOnlyTheCommonFiveFieldEnvelope() {
        Map<String, Object> schemas = schemas();
        Map<String, Object> error = object(schemas, "ApiErrorDTO");
        assertThat(error).isNotNull();
        Map<String, Object> properties = object(error, "properties");

        assertThat(properties).containsOnlyKeys("timestamp", "status", "code", "message", "details");
        assertThat(object(properties, "timestamp"))
                .containsEntry("type", "string")
                .containsEntry("format", "date-time");
        assertThat(object(properties, "status")).containsEntry("type", "integer");
        assertThat(object(properties, "details")).containsEntry("type", "object");
    }

    @Test
    @DisplayName("REQ-OPENAPI-007 - paged location response schema -> GAM envelope rather than Spring Page internals")
    void pagedLocationResponseSchemaShouldUseTheGAMOwnedEnvelope() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        Map<String, Object> paths = object(contract, "paths");
        Map<String, Object> operation = object(object(paths, "/locations"), "get");
        Map<String, Object> responses = object(operation, "responses");
        Map<String, Object> response = object(responses, "200");
        Map<String, Object> mediaType = first(object(response, "content").values());
        Map<String, Object> schema = resolveSchema(contract, object(mediaType, "schema"));

        assertThat(object(schema, "properties")).containsOnlyKeys(
                "items", "page", "size", "totalElements", "totalPages", "first", "last"
        );
    }

    @Test
    @DisplayName("REQ-OPENAPI-004 and REQ-OPENAPI-008 - common response schemas -> UUID/date/timestamp/enum/nullability contract")
    void commonResponseSchemasShouldUseStableConsumerRepresentations() {
        Map<String, Object> schemas = schemas();
        Map<String, Object> member = object(schemas, "MemberRDTO");
        Map<String, Object> event = object(schemas, "EventRDTO");
        Map<String, Object> location = object(schemas, "LocationRDTO");

        assertThat(object(object(member, "properties"), "id"))
                .containsEntry("type", "string")
                .containsEntry("format", "uuid");
        assertThat(object(object(member, "properties"), "birthDate"))
                .containsEntry("type", "string")
                .containsEntry("format", "date");
        assertThat(object(object(event, "properties"), "beginDate"))
                .containsEntry("type", "string")
                .containsEntry("format", "date-time");
        Map<String, Object> eventType = object(object(event, "properties"), "type");
        Object eventTypeValues = eventType.get("enum");
        if (eventTypeValues == null && eventType.get("$ref") != null) {
            String reference = eventType.get("$ref").toString();
            eventTypeValues = object(schemas, reference.substring(reference.lastIndexOf('/') + 1)).get("enum");
        }
        assertThat(eventTypeValues)
                .isInstanceOf(List.class)
                .asList()
                .allSatisfy(value -> assertThat(value.toString()).matches("[A-Z][A-Z0-9_]*"));
        assertThat((List<String>) location.get("required"))
                .contains("id", "name", "city", "state", "countryCode")
                .doesNotContain("street", "postalCode", "latitude", "longitude");
    }

    private Map<String, Object> schemas() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        return object(object(contract, "components"), "schemas");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> first(Collection<Object> values) {
        return (Map<String, Object>) values.iterator().next();
    }

    private Map<String, Object> resolveSchema(Map<String, Object> contract, Map<String, Object> schema) {
        String reference = String.valueOf(schema.get("$ref"));
        String schemaName = reference.substring(reference.lastIndexOf('/') + 1);
        return object(object(object(contract, "components"), "schemas"), schemaName);
    }
}
