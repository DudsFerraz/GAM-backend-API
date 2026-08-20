package br.org.gam.api.configuration;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Configuration - development fixture isolation")
class DevelopmentFixtureConfigurationTest {

    private static final Pattern PROHIBITED_RAW_PASSWORD =
            Pattern.compile("(?<!\\d)123" + "456(?!\\d)");
    private static final String PROHIBITED_LEGACY_PROPERTY =
            "app.dev-fixtures." + "standard-password";
    private static final Pattern RECONSTRUCTABLE_HASH =
            Pattern.compile("(?i)[0-9a-f]{96}");

    @Test
    @DisplayName("REQ-DEV-FIXTURE-001 - ordinary profiles -> development callback location is excluded")
    void ordinaryProfilesShouldExcludeDevelopmentCallbackLocation() throws IOException {
        assertThat(properties("application.properties").getProperty("spring.flyway.locations"))
                .isEqualTo("classpath:db/migration");
        assertThat(properties("application-test.properties").getProperty("spring.flyway.locations"))
                .doesNotContain("classpath:db/dev-migration");
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-001 - development profile -> isolated callback location is explicit")
    void developmentProfileShouldExplicitlyIncludeDevelopmentCallbackLocation() throws IOException {
        assertThat(properties("application-dev.properties").getProperty("spring.flyway.locations"))
                .contains("classpath:db/migration")
                .contains("classpath:db/dev-migration");
    }

    @Test
    @DisplayName("concurrent development tasks -> isolated Compose project, volume, and host port")
    void concurrentDevelopmentTasksShouldUseIsolatedComposeResources() throws IOException {
        Properties defaults = properties("application.properties");
        String compose = Files.readString(Path.of("compose.yml"), StandardCharsets.UTF_8);

        assertThat(defaults.getProperty("spring.docker.compose.arguments[0]"))
                .isEqualTo("--project-name=gam-api-${GAM_DEV_INSTANCE_ID:${CODEX_THREAD_ID:local}}");
        assertThat(compose)
                .contains("\"127.0.0.1::5432\"")
                .doesNotContain("\"5433:5432\"");
    }

    @Test
    @DisplayName("development instance selection -> explicit override, Codex task, then local fallback")
    void developmentInstanceSelectionShouldUseAcceptedPrecedence() throws IOException {
        assertThat(resolvedComposeProjectArgument(Map.of(
                "GAM_DEV_INSTANCE_ID", "explicit_instance",
                "CODEX_THREAD_ID", "ignored-thread"
        ))).isEqualTo("--project-name=gam-api-explicit_instance");
        assertThat(resolvedComposeProjectArgument(Map.of(
                "CODEX_THREAD_ID", "019ff6da-4c99-7502-9577-00bf7cfc8d6a"
        ))).isEqualTo("--project-name=gam-api-019ff6da-4c99-7502-9577-00bf7cfc8d6a");
        assertThat(resolvedComposeProjectArgument(Map.of()))
                .isEqualTo("--project-name=gam-api-local");
    }

    @Test
    @DisplayName("application rerun -> existing task Compose environment remains running")
    void applicationRerunShouldReuseTaskComposeEnvironment() throws IOException {
        Properties defaults = properties("application.properties");

        assertThat(properties("application-dev.properties"))
                .containsEntry("spring.docker.compose.lifecycle-management", "start-only");
        assertThat(defaults.stringPropertyNames())
                .noneMatch(name -> name.equals("spring.docker.compose.stop.command"))
                .noneMatch(name -> name.startsWith("spring.docker.compose.stop.arguments"));
    }

    @Test
    @DisplayName("finished development task -> explicit confirmed volume cleanup helper")
    void finishedDevelopmentTaskShouldHaveExplicitCleanupHelper() throws IOException {
        Path helper = Path.of("scripts", "RemoveDevelopmentEnvironment.java");
        String source = Files.readString(helper, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path output = Files.createTempDirectory("development-environment-cleanup-helper");

        assertThat(compiler).as("JDK compiler").isNotNull();
        assertThat(compiler.run(null, null, null, "-d", output.toString(), helper.toString()))
                .isZero();
        assertThat(source)
                .contains(
                        "System.getenv(\"GAM_DEV_INSTANCE_ID\")",
                        "System.getenv(\"CODEX_THREAD_ID\")",
                        "Console console = System.console()",
                        "\"down\"",
                        "\"--volumes\""
                )
                .doesNotContain("docker volume prune");
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-002 - committed fixture configuration -> no usable credential")
    void committedFixtureConfigurationShouldContainOnlyEmptyCredentialInputs() throws IOException {
        Properties localExample = properties("application-local.properties.example");
        Map<String, String> fixtureInputs = localExample.stringPropertyNames().stream()
                .filter(name -> name.toLowerCase().contains("dev-fixture"))
                .collect(Collectors.toMap(name -> name, localExample::getProperty));

        assertThat(fixtureInputs)
                .hasSizeGreaterThanOrEqualTo(2)
                .allSatisfy((name, value) -> {
                    assertThat(name).doesNotContainIgnoringCase("standard-password");
                    assertThat(value).as("committed value for %s", name).isBlank();
                });

        String callback = resourceText("db/dev-migration/afterMigrate.sql");
        assertThat(callback)
                .doesNotContain("123456");
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-002 - prospective committed repository content -> no delegated credential leak")
    void repositoryContentShouldContainNoDelegatedCredentialLeak()
            throws IOException, InterruptedException {
        List<String> leaks = new ArrayList<>();

        for (Path path : prospectiveCommittedFiles()) {
            String content = new String(
                    Files.readAllBytes(path),
                    StandardCharsets.ISO_8859_1
            );
            if (RECONSTRUCTABLE_HASH.matcher(normalizedSecretMaterial(content)).find()) {
                leaks.add(path + ": reconstructable delegated PBKDF2 hash");
            }

            String[] lines = content.split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                String line = lines[index];
                if (PROHIBITED_RAW_PASSWORD.matcher(line).find()
                        && !isAllowedRawPasswordReference(path, line)) {
                    leaks.add(path + ":" + (index + 1) + ": legacy raw password");
                }
                if (line.contains(PROHIBITED_LEGACY_PROPERTY)) {
                    leaks.add(path + ":" + (index + 1) + ": legacy credential property");
                }
            }
        }

        assertThat(leaks)
                .as("tracked or prospective committed repository content containing "
                        + "a usable or reconstructable delegated credential")
                .isEmpty();
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-002 - known legacy hash fingerprint -> case-insensitive rejection")
    void legacyHashFingerprintShouldBeComparedAfterCaseNormalization() throws IOException {
        String callback = resourceText("db/dev-migration/afterMigrate.sql");

        assertThat(callback)
                .containsPattern(
                        "md5\\s*\\(\\s*lower\\s*\\(\\s*v_password_hash\\s*\\)\\s*\\)"
                                + "\\s*=\\s*'6174bd7cf76eb427ab51a1f3b754c3b1'"
                );
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-014 and REQ-GAM-LOCATION-CATALOG-008 - omitted Oratorio setting -> DBSM code")
    void omittedOratorioLocationSettingShouldDefaultToDbsmCode() throws IOException {
        Properties defaults = properties("application.properties");

        assertThat(defaults)
                .containsEntry(
                        "gam.oratorio.location-code",
                        "${GAM_ORATORIO_LOCATION_CODE:DBSM}"
                )
                .doesNotContainKey("gam.oratorio.location-name");
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-001 and REQ-DEV-FIXTURE-012 - callback maintenance -> warnings and fixed manifest")
    void callbackShouldRetainSecurityWarningsAndManifestMaintenanceSignals() throws IOException {
        String callback = resourceText("db/dev-migration/afterMigrate.sql");

        assertThat(callback)
                .contains(
                        "WARNING: THIS LOCAL-DEVELOPMENT CALLBACK CREATES PRIVILEGED ACCOUNTS",
                        "NEVER ADD THIS LOCATION TO PRODUCTION",
                        "${gamDevFixtureExecutionEnabled}",
                        "${gamDevFixturePasswordHash}",
                        "code = 'DBSM'",
                        "system_managed",
                        "catalog_current",
                        "dev.sudo@example.com",
                        "renata.custom-role@example.com"
                )
                .doesNotContain("gam.oratorio.location-name")
                .doesNotContain("@gmail.com");
        assertThat(callback)
                .as("canonical fixture identities must not be generated at callback runtime")
                .doesNotMatch("(?s).*\\buuidv7\\s*\\(.*");
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-002 and REQ-AUTH-003 - credential helper -> compilable hidden-input procedure")
    void credentialHelperShouldCompileAndProtectTheRawPassword() throws IOException {
        Path helper = Path.of("scripts", "NewDevelopmentFixturePasswordHash.java");
        String source = Files.readString(helper, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path output = Files.createTempDirectory("development-fixture-password-helper");

        assertThat(compiler).as("JDK compiler").isNotNull();
        assertThat(compiler.run(null, null, null, "-d", output.toString(), helper.toString()))
                .isZero();
        assertThat(source)
                .contains(
                        "Console console = System.console()",
                        "console.readPassword(",
                        "MINIMUM_CHARACTERS = 8",
                        "MAXIMUM_CHARACTERS = 128",
                        "PBKDF2WithHmacSHA256",
                        "Arrays.fill(password, '\\0')"
                )
                .doesNotContain("args[")
                .doesNotContain("new String(password)");
    }

    @Test
    @DisplayName("REQ-DEV-FIXTURE-012 - manifest documentation -> callback and local procedure remain linked")
    void manifestDocumentationShouldDescribeTheMaintainedFixtureContract() throws IOException {
        String documentation = Files.readString(
                Path.of("docs", "development-fixture.md"),
                StandardCharsets.UTF_8
        );

        assertThat(documentation)
                .contains(
                        "Development Fixture Policy and Dataset",
                        "java scripts/NewDevelopmentFixturePasswordHash.java",
                        "java scripts/RemoveDevelopmentEnvironment.java",
                        "gam.dev-fixture.execution-enabled",
                        "gam.dev-fixture.password-hash",
                        "01950000-0001-7000-8000-000000000001",
                        "the current `DBSM` system catalog row",
                        "focused fixture verification"
                )
                .doesNotContain("123456")
                .doesNotContain("@gmail.com");
    }

    private static Properties properties(String resourcePath) throws IOException {
        return PropertiesLoaderUtils.loadProperties(new ClassPathResource(resourcePath));
    }

    private static String resolvedComposeProjectArgument(Map<String, Object> environment)
            throws IOException {
        Properties defaults = properties("application.properties");
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(new MapPropertySource("test-environment", environment));
        propertySources.addLast(new PropertiesPropertySource("application-defaults", defaults));

        return new PropertySourcesPropertyResolver(propertySources).resolveRequiredPlaceholders(
                defaults.getProperty("spring.docker.compose.arguments[0]")
        );
    }

    private static String resourceText(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<Path> prospectiveCommittedFiles()
            throws IOException, InterruptedException {
        Process git = new ProcessBuilder(
                "git",
                "ls-files",
                "--cached",
                "--others",
                "--exclude-standard",
                "-z"
        ).start();
        byte[] listedFiles = git.getInputStream().readAllBytes();
        byte[] errors = git.getErrorStream().readAllBytes();
        int exitCode = git.waitFor();

        assertThat(exitCode)
                .as("git ls-files: %s", new String(errors, StandardCharsets.UTF_8))
                .isZero();

        return Stream.of(new String(listedFiles, StandardCharsets.UTF_8).split("\0"))
                .filter(path -> !path.isBlank())
                .map(Path::of)
                .filter(Files::isRegularFile)
                .toList();
    }

    private static boolean isAllowedRawPasswordReference(Path path, String line) {
        String repositoryPath = path.toString().replace('\\', '/');
        if (repositoryPath.equals(
                "docs/requirements/platform/development-fixture-policy-and-dataset.md"
        )) {
            return line.strip().equals(
                    "configuration keys and explanatory instructions. The former `123"
                            + "456`"
            );
        }
        if (repositoryPath.equals(
                "src/test/java/br/org/gam/api/configuration/"
                        + "DevelopmentFixtureConfigurationTest.java"
        )) {
            String negativeAssertion = ".doesNotContain(\"123" + "456\")";
            return line.strip().equals(negativeAssertion)
                    || line.strip().equals(negativeAssertion + ";");
        }
        return false;
    }

    private static String normalizedSecretMaterial(String source) {
        return source.replaceAll("[\\s'\"+|]", "").toLowerCase(Locale.ROOT);
    }
}
