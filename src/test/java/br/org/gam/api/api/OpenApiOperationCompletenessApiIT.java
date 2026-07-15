package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@ApiTest
@FunctionalTest
@IntegrationTest
@DisplayName("API - OpenAPI operation completeness")
class OpenApiOperationCompletenessApiIT extends AbstractOpenApiDocumentationApiIT {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final Set<String> CONSUMER_TAGS = Set.of(
            "Authentication", "Accounts", "Members", "Membership Solicitations", "Events", "Locations", "Presences", "RBAC"
    );

    @Test
    @DisplayName("REQ-OPENAPI-003, REQ-OPENAPI-004, and REQ-OPENAPI-012 - every public operation -> complete consumer contract")
    void everyPublicOperationShouldHaveStableConsumerFacingDocumentation() {
        Map<String, Object> contract = openApiContract().jsonPath().getMap("$");
        List<Map<String, Object>> operations = operations(contract);
        SoftAssertions assertions = new SoftAssertions();

        assertions.assertThat(operations).isNotEmpty();
        assertions.assertThat(operations)
                .extracting(operation -> operation.get("operationId"))
                .doesNotContainNull()
                .doesNotHaveDuplicates()
                .allSatisfy(operationId -> assertions.assertThat(operationId.toString())
                        .matches("[a-z][A-Za-z0-9]*"));

        for (Map<String, Object> operation : operations) {
            String operationId = String.valueOf(operation.get("operationId"));
            assertions.assertThat(strings(operation, "tags"))
                    .as("%s tag", operationId)
                    .hasSize(1)
                    .allSatisfy(tag -> assertions.assertThat(CONSUMER_TAGS).contains(tag));
            assertions.assertThat(operation.get("summary"))
                    .as("%s summary", operationId)
                    .isInstanceOf(String.class)
                    .asString()
                    .isNotBlank();
            assertions.assertThat(operation.get("description"))
                    .as("%s purpose", operationId)
                    .isInstanceOf(String.class)
                    .asString()
                    .isNotBlank();

            Map<String, Object> responses = object(operation, "responses");
            assertions.assertThat(responses).as("%s responses", operationId).isNotEmpty();
            assertions.assertThat(responses.keySet())
                    .as("%s error responses", operationId)
                    .anySatisfy(status -> assertions.assertThat(status).startsWith("4"));
            assertExamples(operationId, operation, assertions);
        }

        assertions.assertAll();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> operations(Map<String, Object> contract) {
        List<Map<String, Object>> operations = new ArrayList<>();
        Map<String, Object> paths = object(contract, "paths");

        for (Object pathItem : paths.values()) {
            Map<String, Object> methods = (Map<String, Object>) pathItem;
            methods.forEach((method, operation) -> {
                if (HTTP_METHODS.contains(method)) {
                    operations.add((Map<String, Object>) operation);
                }
            });
        }
        return operations;
    }

    @SuppressWarnings("unchecked")
    private void assertExamples(String operationId, Map<String, Object> operation, SoftAssertions assertions) {
        Map<String, Object> requestBody = object(operation, "requestBody");
        if (requestBody != null) {
            assertions.assertThat(hasExample(object(requestBody, "content").values()))
                    .as("%s request example", operationId)
                    .isTrue();
        }

        Map<String, Object> responses = object(operation, "responses");
        for (Object responseValue : responses.values()) {
            Map<String, Object> response = (Map<String, Object>) responseValue;
            Map<String, Object> content = object(response, "content");
            if (content != null) {
                assertions.assertThat(hasExample(content.values()))
                        .as("%s response example", operationId)
                        .isTrue();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasExample(Collection<Object> mediaTypes) {
        return mediaTypes.stream()
                .map(mediaType -> (Map<String, Object>) mediaType)
                .anyMatch(mediaType -> mediaType.containsKey("example") || mediaType.containsKey("examples"));
    }
}
