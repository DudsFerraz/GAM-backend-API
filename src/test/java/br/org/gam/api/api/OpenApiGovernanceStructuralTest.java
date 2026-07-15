package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structure - OpenAPI governance workflow")
class OpenApiGovernanceStructuralTest {

    @Test
    @DisplayName("REQ-OPENAPI-009 - Maven build -> stable openapi profile orchestrates contract generation")
    void buildShouldProvideTheOpenApiProfile() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try (InputStream pom = Files.newInputStream(Path.of("pom.xml"))) {
            Document document = factory.newDocumentBuilder().parse(pom);
            XPath xpath = XPathFactory.newInstance().newXPath();
            Node profile = (Node) xpath.evaluate(
                    "/project/profiles/profile[id='openapi']",
                    document,
                    XPathConstants.NODE
            );

            assertThat(profile).isNotNull();
        }
    }

    @Test
    @DisplayName("REQ-OPENAPI-011 - repository automation -> contract generation, Spectral, oasdiff, and release artifact checks")
    void repositoryAutomationShouldGovernTheGeneratedContract() throws Exception {
        List<Path> workflows;
        try (var files = Files.list(Path.of(".github", "workflows"))) {
            workflows = files.filter(Files::isRegularFile).toList();
        }
        String automation = workflows.stream()
                .map(this::read)
                .reduce("", String::concat)
                .toLowerCase();

        assertThat(automation).contains("-popenapi verify", "spectral", "oasdiff", "openapi.yaml");
        assertThat(Files.exists(Path.of(".spectral.yaml"))).isTrue();
    }

    private String read(Path file) {
        try {
            return Files.readString(file);
        } catch (Exception exception) {
            throw new AssertionError("Could not read workflow " + file, exception);
        }
    }
}
