package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@StructuralTest
@DisplayName("Structure - OpenAPI operation metadata")
class OpenApiOperationMetadataStructuralTest {

    private static final Pattern HANDLER_MAPPING = Pattern.compile("@(Get|Post|Put|Patch|Delete)Mapping");

    @Test
    @DisplayName("REQ-OPENAPI-003 - controller handler -> explicit stable OpenAPI operation metadata")
    void everyControllerHandlerShouldDeclareAnExplicitOperationId() throws Exception {
        List<Path> controllers;
        try (var sources = Files.walk(Path.of("src", "main", "java"))) {
            controllers = sources
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .toList();
        }

        SoftAssertions assertions = new SoftAssertions();
        for (Path controller : controllers) {
            List<String> lines = Files.readAllLines(controller);
            for (int index = 0; index < lines.size(); index++) {
                if (HANDLER_MAPPING.matcher(lines.get(index)).find()) {
                    String annotations = String.join("\n", lines.subList(Math.max(0, index - 12), index));
                    assertions.assertThat(annotations)
                            .as("%s handler at line %s", controller, index + 1)
                            .contains("@Operation", "operationId");
                }
            }
        }

        assertions.assertAll();
    }
}
