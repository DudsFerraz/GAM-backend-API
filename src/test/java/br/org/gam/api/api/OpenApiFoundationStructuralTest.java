package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
@DisplayName("Structure - OpenAPI foundation")
class OpenApiFoundationStructuralTest {

    @Test
    @DisplayName("REQ-OPENAPI-001 - build -> Springdoc Web MVC UI integration is available at runtime")
    void buildShouldIncludeSpringdocWebMvcUiIntegration() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try (InputStream pom = Files.newInputStream(Path.of("pom.xml"))) {
            Document document = factory.newDocumentBuilder().parse(pom);
            XPath xpath = XPathFactory.newInstance().newXPath();
            Node springdoc = (Node) xpath.evaluate(
                    "/project/dependencies/dependency["
                            + "groupId='org.springdoc' and artifactId='springdoc-openapi-starter-webmvc-ui']",
                    document,
                    XPathConstants.NODE
            );

            assertThat(springdoc).isNotNull();
        }
    }
}
