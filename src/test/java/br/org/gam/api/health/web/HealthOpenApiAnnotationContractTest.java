package br.org.gam.api.health.web;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;

@UnitTest
@StructuralTest
@DisplayName("Health OpenAPI response metadata")
class HealthOpenApiAnnotationContractTest {

    @Test
    @DisplayName("REQ-OPS-011 - both health responses require status and document Cache-Control")
    void healthResponsesShouldDocumentTheMinimalSchemaAndNoStoreHeader() throws Exception {
        Method handler = HealthController.class.getMethod("getHealth");
        ApiResponse[] responses = handler.getAnnotationsByType(ApiResponse.class);
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(responses)
                .as("health response annotations")
                .hasSize(2);
        for (ApiResponse response : responses) {
            Content[] contents = response.content();
            softly.assertThat(contents)
                    .as("response content for %s", response.responseCode())
                    .isNotEmpty();
            if (contents.length > 0) {
                softly.assertThat(contents[0].schema().requiredProperties())
                        .as("required health response properties for %s", response.responseCode())
                        .contains("status");
            }

            Header cacheControl = Arrays.stream(response.headers())
                    .filter(header -> "Cache-Control".equalsIgnoreCase(header.name()))
                    .findFirst()
                    .orElse(null);
            softly.assertThat(cacheControl)
                    .as("Cache-Control response header for %s", response.responseCode())
                    .isNotNull();
            if (cacheControl != null) {
                softly.assertThat(cacheControl.required())
                        .as("Cache-Control required flag for %s", response.responseCode())
                        .isTrue();
                softly.assertThat(cacheControl.schema().type())
                        .as("Cache-Control schema type for %s", response.responseCode())
                        .isEqualTo("string");
            }
        }
        softly.assertAll();
    }
}
