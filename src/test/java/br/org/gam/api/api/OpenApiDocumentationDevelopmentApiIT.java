package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@ActiveProfiles(profiles = "dev")
@TestPropertySource(properties = "spring.docker.compose.enabled=false")
@DisplayName("API - OpenAPI documentation in development")
class OpenApiDocumentationDevelopmentApiIT extends AbstractOpenApiDocumentationApiIT {

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("REQ-OPENAPI-002 - development Swagger UI configuration -> request execution is enabled for GAM HTTP methods")
    void developmentSwaggerUiShouldEnableInteractiveRequestExecution() {
        Map<String, Object> configuration = swaggerUiConfiguration();
        Object configuredMethods = configuration.get("supportedSubmitMethods");

        if (configuredMethods == null) {
            return;
        }

        assertThat((List<String>) configuredMethods)
                .contains("get", "post", "patch", "delete");
    }
}
