package br.org.gam.api.production;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structure - public API prefix routing")
class PublicApiPrefixRoutingStructuralTest {

    private static final Path CADDYFILE = Path.of("deploy", "production", "Caddyfile");
    private static final Path COMPOSE = Path.of("deploy", "production", "compose.yml");
    private static final Path RELEASE_PLAYBOOK = Path.of(
            "deploy", "production", "ansible", "deploy-release.yml"
    );

    @Test
    @DisplayName("REQ-WEB-014 - Caddy API boundary -> exact segment match and one prefix removal before proxying")
    void caddyShouldMatchTheCompleteApiSegmentAndRemoveItExactlyOnce() throws IOException {
        String caddy = requiredFile(CADDYFILE);
        Matcher matcher = Pattern.compile("(?m)^\\s*@api\\s+path\\s+([^\\r\\n#]+)$").matcher(caddy);

        assertThat(matcher.find()).as("named API path matcher").isTrue();
        assertThat(Arrays.asList(matcher.group(1).trim().split("\\s+")))
                .containsExactlyInAnyOrder("/api", "/api/*");

        String apiHandler = section(caddy, "handle @api {", "handle {");
        assertThat(apiHandler).contains("reverse_proxy backend:8080");
        assertThat(Pattern.compile("(?m)^\\s*uri\\s+strip_prefix\\s+/api\\s*$")
                .matcher(apiHandler).results().toList())
                .as("single public prefix removal")
                .hasSize(1);
        assertThat(apiHandler).doesNotContain("uri strip_prefix /api/api");
        assertOrdered(apiHandler, "uri strip_prefix /api", "reverse_proxy backend:8080");
    }

    @Test
    @DisplayName("REQ-WEB-015 - trusted forwarding -> proxy-owned original URI and no Location repair")
    void caddyShouldPreserveOriginalPublicUriWithoutRewritingLocation() throws IOException {
        String caddy = requiredFile(CADDYFILE);
        String apiHandler = section(caddy, "handle @api {", "handle {");
        Matcher originalUri = Pattern.compile(
                "(?m)^\\s*header_up\\s+([A-Za-z0-9-]+)\\s+\\{http\\.request\\.orig_uri}\\s*$"
        ).matcher(apiHandler);

        assertThat(originalUri.find()).as("trusted original public URI header").isTrue();
        assertThat(apiHandler).contains("header_up -" + originalUri.group(1));
        assertOrdered(
                apiHandler,
                "header_up -" + originalUri.group(1),
                "header_up " + originalUri.group(1) + " {http.request.orig_uri}"
        );
        assertThat(caddy).doesNotMatch("(?is).*header_down\\s+[^\\r\\n]*location.*");
    }

    @Test
    @DisplayName("REQ-OPS-015 - readiness checks -> private /health and public /api/health")
    void deploymentShouldKeepPrivateAndPublicReadinessPathsDistinct() throws IOException {
        String compose = requiredFile(COMPOSE);
        String playbook = requiredFile(RELEASE_PLAYBOOK);

        assertThat(compose)
                .contains("http://127.0.0.1:8080/health")
                .doesNotContain("http://127.0.0.1:8080/api/health");
        assertThat(playbook)
                .contains("http://127.0.0.1:8080/health")
                .contains("{{ gam_public_origin }}/api/health")
                .doesNotContain("http://127.0.0.1:8080/api/health");
    }

    private static String requiredFile(Path path) throws IOException {
        assertThat(Files.exists(path)).as("required production artifact: %s", path).isTrue();
        return Files.readString(path);
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        assertThat(start).as("start marker '%s'", startMarker).isGreaterThanOrEqualTo(0);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertThat(end).as("end marker '%s'", endMarker).isGreaterThan(start);
        return source.substring(start, end);
    }

    private static void assertOrdered(String source, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = source.indexOf(marker);
            assertThat(current).as("marker '%s'", marker).isGreaterThan(previous);
            previous = current;
        }
    }
}
