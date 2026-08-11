package br.org.gam.api.health.web;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@StructuralTest
@DisplayName("Structure - backend readiness route")
class HealthRoutingContractTest {

    @Test
    @DisplayName("REQ-OPS-015 - Health controller -> /health without a backend /api alias")
    void healthControllerShouldUseOnlyTheBackendRelativePath() {
        RequestMapping mapping = HealthController.class.getAnnotation(RequestMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/health");
    }
}
