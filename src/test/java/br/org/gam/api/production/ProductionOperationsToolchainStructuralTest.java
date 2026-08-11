package br.org.gam.api.production;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@StructuralTest
@DisplayName("Structure - production operations verification toolchain")
class ProductionOperationsToolchainStructuralTest {

    private static final Path ANSIBLE = Path.of("operations", "ansible");
    private static final Path PYTHON_REQUIREMENTS = ANSIBLE.resolve("requirements-test.txt");
    private static final Path COLLECTION_REQUIREMENTS = ANSIBLE.resolve("collections").resolve("requirements.yml");
    private static final Path PRODUCTION_VARIABLES = ANSIBLE.resolve("group_vars").resolve("production.yml");
    private static final Path CI_WORKFLOW = Path.of(".github", "workflows", "ci-testes.yml");
    private static final Pattern COLLECTION_VERSION = Pattern.compile(
            "(?m)^\\s*version:\\s*[\\\"']?([^\\\"'\\s#]+)"
    );

    @Test
    @DisplayName("Operational Python contracts - clean verification -> pinned PyYAML dependency is versioned")
    void operationalPythonContractsShouldHaveAPinnedDependencyEnvironment() {
        assertThat(PYTHON_REQUIREMENTS)
                .as("versioned dependency input for the operational Python contract suite")
                .exists()
                .isRegularFile();

        List<String> dependencies = readLines(PYTHON_REQUIREMENTS).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();

        assertThat(dependencies)
                .as("operational Python dependencies")
                .isNotEmpty()
                .allMatch(
                        dependency -> dependency.matches("[A-Za-z0-9_.-]+==[A-Za-z0-9_.+!-]+"),
                        "every dependency must use an exact reproducible pin"
                );
        assertThat(dependencies)
                .as("PyYAML supplies the suite's yaml import")
                .anyMatch(dependency -> dependency.matches("(?i)pyyaml==[0-9]+(?:\\.[0-9]+)+(?:[A-Za-z0-9_.+-]*)?"));
    }

    @Test
    @DisplayName("Operational Python contracts - canonical CI -> provisions pinned dependencies and executes the suite")
    void canonicalCiShouldExecuteOperationalPythonContractsFromACleanRunner() {
        String workflow = read(CI_WORKFLOW).toLowerCase(Locale.ROOT).replace('\\', '/');

        assertThat(workflow)
                .as("a controlled Python runtime must be provisioned on the clean CI runner")
                .contains("actions/setup-python@");
        assertThat(workflow)
                .as("CI must install the versioned operational dependency set")
                .containsPattern(
                        "(?is)(?:python\\s+-m\\s+)?pip\\s+install[^\\n]*operations/ansible/requirements-test\\.txt"
                );
        assertThat(workflow)
                .as("the canonical CI gate must execute all operational Python contracts")
                .containsPattern(
                        "(?is)python(?:3)?(?:\\s+-m\\s+unittest)?\\s+operations/ansible/test_production_backup_aws_contracts\\.py"
                );
        assertThat(workflow)
                .as("canonical CI must execute provider-response scenarios through the pinned Ansible runtime")
                .containsPattern(
                        "(?is)python(?:3)?\\s+operations/ansible/test_better_stack_provider_scenarios\\.py"
                );
        assertThat(workflow)
                .as("canonical CI must install the exact Ansible collection set used by provider scenarios")
                .containsPattern(
                        "(?is)ansible-galaxy\\s+collection\\s+install[^\\n]*operations/ansible/collections/requirements\\.yml"
                );
    }

    @Test
    @DisplayName("REQ-OPS-010 - Ansible collections -> every dependency uses one exact validated version")
    void ansibleCollectionsShouldUseExactValidatedVersions() {
        Matcher matcher = COLLECTION_VERSION.matcher(read(COLLECTION_REQUIREMENTS));
        List<String> versions = new ArrayList<>();
        while (matcher.find()) {
            versions.add(matcher.group(1));
        }

        assertThat(versions)
                .as("Ansible collection versions")
                .isNotEmpty()
                .allMatch(
                        version -> version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?"),
                        "minimum ranges and other open-ended selectors are not reproducible pins"
                );
    }

    @Test
    @DisplayName("REQ-OPS-007/010 and ADR-0029 - Better Stack resource identity -> IDs are discovered, not external prerequisites")
    void betterStackResourceIdsShouldNotBeExternalInputs() {
        String variables = read(PRODUCTION_VARIABLES);

        assertThat(variables)
                .as("provider resource IDs must come from Ansible discovery and create responses")
                .doesNotContain(
                        "BETTER_STACK_COLLECTOR_ID",
                        "BETTER_STACK_DASHBOARD_ID",
                        "BETTER_STACK_PROXY_CHART_ID",
                        "BETTER_STACK_BACKEND_CHART_ID",
                        "BETTER_STACK_POSTGRESQL_CHART_ID",
                        "BETTER_STACK_FILESYSTEM_CHART_ID"
                );
        assertThat(variables)
                .as("manual API-token and collector-secret custody remains external")
                .contains("BETTER_STACK_API_TOKEN", "BETTER_STACK_COLLECTOR_SECRET");
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException exception) {
            throw new AssertionError("Could not read " + path, exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("Could not read " + path, exception);
        }
    }
}
