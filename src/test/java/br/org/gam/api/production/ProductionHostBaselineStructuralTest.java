package br.org.gam.api.production;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structure - production Ubuntu 24.04 Ansible host baseline")
class ProductionHostBaselineStructuralTest {

    private static final Path ANSIBLE_ROOT = Path.of("operations", "ansible");
    private static final Path INVENTORY_DIRECTORY = ANSIBLE_ROOT.resolve("inventory");
    private static final Path PLAYBOOK_DIRECTORY = ANSIBLE_ROOT.resolve("playbooks");
    private static final Path ROLE_DIRECTORY = ANSIBLE_ROOT.resolve("roles");
    private static final Set<String> REQUIRED_ROLES = Set.of(
            "ssh-hardening",
            "operations-users",
            "docker",
            "firewall",
            "directories",
            "secret-inputs"
    );

    @Test
    @DisplayName("REQ-OPS-001 and ADR-0024 - production inventory -> one Ubuntu 24.04 VPS")
    void productionInventoryShouldDescribeOneInitialHost() throws IOException {
        Path inventory = requiredSingleFile(
                INVENTORY_DIRECTORY,
                path -> path.getFileName().toString().matches("(?i)production\\.(ini|ya?ml)")
        );
        String content = read(inventory);

        assertThat(content)
                .as("production inventory group")
                .containsPattern("(?im)\\bproduction\\b");
        assertThat(count(content, "(?im)\\bansible_host\\s*[:=]")).isEqualTo(1);
        assertThat(content)
                .doesNotContainPattern("(?im)\\bstaging\\b|\\bkvm1\\b");

        Path baseline = baselinePlaybook();
        String playbook = read(baseline);
        assertThat(playbook)
                .containsPattern("(?im)^\\s*hosts:\\s*production\\s*$")
                .containsPattern("(?im)^\\s*become:\\s*true\\s*$")
                .containsPattern("(?is)Ubuntu")
                .containsPattern("(?is)24\\.04|major_version.*24");
    }

    @Test
    @DisplayName("ADR-0024 - Ubuntu guard -> rejects every version other than 24.04 LTS")
    void baselineShouldRequireTheExactUbuntu2404Release() throws IOException {
        String playbook = read(baselinePlaybook());

        assertThat(playbook)
                .containsPattern(
                        "(?im)ansible_facts\\.distribution_version\\s*==\\s*['\"]24\\.04['\"]"
                );
    }

    @Test
    @DisplayName("REQ-OPS-010 and ADR-0024 - baseline entrypoint -> versioned host roles only")
    void baselineEntrypointShouldContainOnlyTheAcceptedHostBaseline() throws IOException {
        String playbook = read(baselinePlaybook());
        String normalizedPlaybook = normalized(playbook);

        REQUIRED_ROLES.forEach(role ->
                assertThat(normalizedPlaybook)
                        .as("baseline role %s", role)
                        .contains(normalized(role))
        );
        assertThat(normalizedPlaybook)
                .doesNotContain("compose", "deployment", "backup", "aws");

        assertThat(roleNames())
                .as("roles owned by the initial host-baseline scope")
                .containsExactlyInAnyOrderElementsOf(REQUIRED_ROLES.stream()
                        .map(ProductionHostBaselineStructuralTest::normalized)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    @DisplayName("REQ-OPS-002 - SSH hardening -> no root or password-based administrative login")
    void sshHardeningShouldKeepAdministrativeAccessRestricted() throws IOException {
        String role = readRole("ssh-hardening");

        assertThat(role)
                .containsPattern("(?im)permit[_ ]?root[_ ]?login\\s*[: ]\\s*(no|false)")
                .containsPattern("(?im)password[_ ]?authentication\\s*[: ]\\s*(no|false)")
                .containsPattern("(?im)(allow[_ ]?(groups|users)|ssh[_-]?allowed[_-]?cidrs)");
        assertThat(role)
                .doesNotMatch("(?im).*permit[_ ]?root[_ ]?login\\s*[: ]\\s*(yes|true).*")
                .doesNotMatch("(?im).*password[_ ]?authentication\\s*[: ]\\s*(yes|true).*");
    }

    @Test
    @DisplayName("REQ-OPS-002 - firewall integration -> public proxy only, SSH separately restricted")
    void firewallShouldExposeOnlyTheProxyAndRestrictedOperationsChannel() throws IOException {
        String role = readRole("firewall");

        assertThat(role)
                .containsPattern("(?im)\\b80\\b")
                .containsPattern("(?im)\\b443\\b")
                .containsPattern("(?im)(default|incoming)[^\\r\\n]*(deny|reject)")
                .containsPattern("(?im)(ssh|admin)[^\\r\\n]*(cidr|from|source)");
        assertThat(role)
                .doesNotMatch("(?im).*\\b(?:5432|8080)(?:/tcp)?\\b.*")
                .doesNotMatch("(?im).*\\b22(?:/tcp)?\\b[^\\r\\n]*(?:0\\.0\\.0\\.0/0|::/0).*");
    }

    @Test
    @DisplayName("REQ-OPS-002 - firewall integration -> no other numeric public port is allowed")
    void firewallShouldHaveExactlyTheTwoPublicProxyPortRules() throws IOException {
        String role = readRole("firewall");

        assertThat(matches(role, "(?m)^\\s*-\\s*(\\d+)/tcp\\s*$"))
                .containsExactlyInAnyOrder("80", "443");
    }

    @Test
    @DisplayName("REQ-OPS-002 - firewall integration -> restricted SSH rule precedes activation")
    void firewallShouldInstallRestrictedSshBeforeEnabling() throws IOException {
        String role = readRole("firewall").toLowerCase(java.util.Locale.ROOT);
        int sshRule = role.indexOf("allow ssh only from approved operator cidrs");
        int enableFirewall = role.indexOf("enable the deny-by-default host firewall");

        assertThat(sshRule).isGreaterThanOrEqualTo(0);
        assertThat(enableFirewall).isGreaterThan(sshRule);
    }

    @Test
    @DisplayName("REQ-OPS-002 - firewall status failure -> boundary enforcement fails closed")
    void firewallStatusFailureShouldNotSkipBoundaryEnforcement() throws IOException {
        String role = readRole("firewall");

        assertThat(role)
                .doesNotContainPattern("(?im)^\\s*failed_when:\\s*false\\s*$")
                .doesNotContainPattern("(?im)firewall_status\\.rc\\s*==\\s*0");
    }

    @Test
    @DisplayName("REQ-OPS-002 - firewall convergence -> exact rule-scoped state rejects broad rules")
    void firewallConvergenceShouldUseExactRuleScopedStateChecks() throws IOException {
        Path check = requiredSingleFile(
                ANSIBLE_ROOT,
                path -> path.getFileName().toString().matches("(?i).*idempot.*")
        );
        String configuration = readRole("firewall") + read(check);

        assertThat(configuration)
                .containsPattern("(?is)(?:ufw\\s+status\\s+numbered|stdout_lines)")
                .containsPattern("(?is)(?:\\^|\\\\\\^).*?(?:22/tcp|firewall_ssh_port)")
                .containsPattern("(?is)(?:Anywhere|0\\.0\\.0\\.0/0|::/0)")
                .containsPattern(
                        "(?is)(?:not|reject|fail|assert|exit\\s+1).*?(?:Anywhere|0\\.0\\.0\\.0/0|::/0)"
                );
    }

    @Test
    @DisplayName("REQ-OPS-013 and ADR-0024 - SSH bootstrap -> root path transitions to gamops")
    void sshBootstrapShouldExposeSeparateRootAndGamopsPaths() throws IOException {
        Path inventory = requiredSingleFile(
                INVENTORY_DIRECTORY,
                path -> path.getFileName().toString().matches("(?i)production\\.(ini|ya?ml)")
        );
        String configuration = read(inventory)
                + read(baselinePlaybook())
                + readRole("operations-users");

        assertThat(configuration)
                .containsPattern("(?im)(?:ansible_user|remote_user|bootstrap_user)\\s*[:=][^\\r\\n]*\\broot\\b")
                .containsPattern("(?im)(?:ansible_user|remote_user|bootstrap_user)\\s*[:=][^\\r\\n]*\\bgamops\\b");
    }

    @Test
    @DisplayName("REQ-OPS-013 - bootstrap -> real host and restricted CIDR inputs")
    void bootstrapShouldRequireRealHostAndNetworkInputs() throws IOException {
        Path inventory = requiredSingleFile(
                INVENTORY_DIRECTORY,
                path -> path.getFileName().toString().matches("(?i)production\\.(ini|ya?ml)")
        );

        assertThat(read(inventory))
                .doesNotContain("kvm2.example.invalid", "198.51.100.0/24");
    }

    @Test
    @DisplayName("REQ-OPS-013 - bootstrap -> non-empty controller-supplied operations key")
    void bootstrapShouldRequireNonEmptyOperationsKeyInput() throws IOException {
        Path inventory = requiredSingleFile(
                INVENTORY_DIRECTORY,
                path -> path.getFileName().toString().matches("(?i)production\\.(ini|ya?ml)")
        );
        String inventoryContent = read(inventory);
        String baseline = read(baselinePlaybook());
        String users = readRole("operations-users");
        String keyValidation = "(?is)(?:ansible\\.builtin\\.assert|\\bassert:|\\bfail:).*?"
                + "(?:public|authorized).*?key.*?"
                + "(?:length\\s*>\\s*0|non[- ]?empty|required)";
        assertThat(
                matchesPattern(inventoryContent, keyValidation)
                        || matchesPattern(baseline, keyValidation)
                        || matchesPattern(users, keyValidation)
        ).as("bootstrap must reject an empty controller-supplied operations public key")
                .isTrue();
    }

    @Test
    @DisplayName("REQ-OPS-013 - bootstrap inputs -> reject reserved documentation host and CIDR ranges")
    void bootstrapInputValidationShouldRejectReservedDocumentationVariants() throws IOException {
        String playbook = read(baselinePlaybook());

        assertThat(playbook)
                .containsPattern("(?is)198\\.51\\.100\\.")
                .containsPattern("(?is)203\\.0\\.113\\.")
                .containsPattern("(?is)2001:db8")
                .containsPattern("(?is)(?:ansible\\.utils\\.ipaddr|regex_search|regex_match|prefixlen|network_address|subnet)");
    }

    @Test
    @DisplayName("ADR-0024 - Docker baseline -> Engine and Compose plugin are installed")
    void dockerRoleShouldInstallTheAcceptedRuntimePrerequisites() throws IOException {
        String role = readRole("docker");

        assertThat(role)
                .containsPattern("(?im)\\bdocker-(?:ce|io)\\b")
                .containsPattern("(?im)\\bdocker-compose-plugin\\b");
        assertThat(role)
                .doesNotMatch("(?im)^\\s*[-\\w ]*docker-compose\\s*$");
    }

    @Test
    @DisplayName("REQ-OPS-010 - operations users and directories -> managed non-root host baseline")
    void operationsUsersAndDirectoriesShouldBeManagedByDedicatedRoles() throws IOException {
        String users = readRole("operations-users");
        String directories = readRole("directories");

        assertThat(users)
                .containsPattern("(?im)ansible\\.builtin\\.user|^\\s*user:")
                .containsPattern("(?im)(operations|ops)[_-]?user")
                .containsPattern("(?im)\\bsudo\\b")
                .doesNotMatch("(?im)^\\s*name:\\s*root\\s*$");
        assertThat(directories)
                .containsPattern("(?im)ansible\\.builtin\\.file|^\\s*file:")
                .containsPattern("(?im)^\\s*state:\\s*directory\\s*$");
        assertThat(count(directories, "(?im)^\\s*state:\\s*directory\\s*$"))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("REQ-OPS-013 - gamops -> validated mode-0440 passwordless privilege escalation")
    void operationsUserShouldHaveValidatedPasswordlessPrivilegeEscalation() throws IOException {
        String users = readRole("operations-users");

        assertThat(users)
                .containsPattern("(?im)mode:\\s*['\"]?0440['\"]?")
                .containsPattern("(?im)validate:\\s*[^\\r\\n]*visudo[^\\r\\n]*%s")
                .containsPattern("(?im)NOPASSWD");
    }

    @Test
    @DisplayName("REQ-OPS-013 - hardening -> gamops connection and privilege proof come first")
    void hardeningShouldFollowGamopsConnectionAndPrivilegeVerification() throws IOException {
        String playbook = read(baselinePlaybook());
        String normalizedPlaybook = playbook.toLowerCase(java.util.Locale.ROOT);
        int hardening = normalizedPlaybook.indexOf("ssh-hardening");
        int firewall = normalizedPlaybook.indexOf("firewall");

        assertThat(hardening).isGreaterThanOrEqualTo(0);
        assertThat(firewall).isGreaterThan(hardening);
        String beforeHardening = playbook.substring(0, hardening);
        assertThat(beforeHardening)
                .containsPattern(
                        "(?is)(?:verify|prove|test|wait_for_connection|connection).*gamops"
                                + "|gamops.*(?:verify|prove|test|wait_for_connection|connection)"
                )
                .containsPattern("(?is)(?:privilege|become|sudo).*gamops|gamops.*(?:privilege|become|sudo)");
    }

    @Test
    @DisplayName("REQ-OPS-010 - secret inputs -> external recoverable values are never committed")
    void secretInputsShouldUseAnExternalRecoverableSource() throws IOException, InterruptedException {
        String role = readRole("secret-inputs");
        Path example = requiredSingleFile(
                ANSIBLE_ROOT,
                path -> path.getFileName().toString().matches("(?i).*secret.*\\.(example|sample|template)")
        );

        assertThat(role)
                .containsPattern("(?is)lookup\\s*\\(")
                .containsPattern("(?is)(file|env|vault)")
                .containsPattern("(?im)no_log:\\s*true")
                .containsPattern("(?im)(secret|credential)[_-]?(input|file|path)")
                .containsPattern("(?im)secret_input_target_file[^\\r\\n]*/etc/gam/secrets")
                .containsPattern("(?im)secret_input_mode[^\\r\\n]*0600");
        assertThat(read(example))
                .containsPattern("(?im)(file|path|vault)")
                .doesNotMatch("(?im)^\\s*(?:password|secret|token|private[_-]?key)\\s*:\\s*[^$<{#\\s].*$");
        assertThat(read(Path.of(".gitignore")))
                .containsPattern("(?im)(private-data|secret|vault|credential)");
        assertThat(gitCheckIgnore("operations/ansible/secrets.env", false))
                .as("local Ansible secret input ignore rule")
                .contains("operations/ansible/secrets.*")
                .contains("operations/ansible/secrets.env");
        assertThat(gitCheckIgnore("operations/ansible/secrets.example", true))
                .as("sanitized Ansible secret example exception")
                .contains("!operations/ansible/secrets.example")
                .contains("operations/ansible/secrets.example");
    }

    @Test
    @DisplayName("REQ-OPS-010 - idempotency check -> successful real apply is followed by a real replay")
    void baselineShouldProvideARepeatableIdempotencyCheck() throws IOException {
        Path check = requiredSingleFile(
                ANSIBLE_ROOT,
                path -> path.getFileName().toString().matches("(?i).*idempot.*")
        );
        String content = read(check);
        List<String> invocations = playbookInvocations(content);

        assertThat(content)
                .containsPattern("(?is)ansible-playbook")
                .containsPattern("(?is)--diff");
        assertThat(invocations).hasSizeGreaterThanOrEqualTo(2);
        assertThat(invocations.subList(0, 2))
                .allSatisfy(invocation -> assertThat(invocation).doesNotContain("--check"));
    }

    @Test
    @DisplayName("ADR-0024 - idempotency check -> replay reports zero changes")
    void idempotencyCheckShouldAssertAZeroChangeReplay() throws IOException {
        Path check = requiredSingleFile(
                ANSIBLE_ROOT,
                path -> path.getFileName().toString().matches("(?i).*idempot.*")
        );

        assertThat(read(check))
                .containsPattern("(?im)changed\\s*=\\s*0");
    }

    @Test
    @DisplayName("ADR-0024 - idempotency check -> applies baseline before replay validation")
    void idempotencyCheckShouldApplyThenReplay() throws IOException {
        Path check = requiredSingleFile(
                ANSIBLE_ROOT,
                path -> path.getFileName().toString().matches("(?i).*idempot.*")
        );
        List<String> invocations = playbookInvocations(read(check));

        assertThat(invocations).hasSizeGreaterThanOrEqualTo(2);
        assertThat(invocations.get(0)).doesNotContain("--check");
        assertThat(invocations.get(1))
                .doesNotContain("--check")
                .contains("site.yml")
                .contains("--diff");
    }

    @Test
    @DisplayName("REQ-OPS-013 and ADR-0024 - idempotency replay -> steady-state gamops path")
    void idempotencyReplayShouldUseTheGamopsSteadyStatePath() throws IOException {
        Path check = requiredSingleFile(
                ANSIBLE_ROOT,
                path -> path.getFileName().toString().matches("(?i).*idempot.*")
        );
        List<String> invocations = playbookInvocations(read(check));

        assertThat(invocations).hasSizeGreaterThanOrEqualTo(2);
        assertThat(invocations.get(1))
                .containsPattern("(?i)gamops|steady[-_ ]state");
    }

    @Test
    @DisplayName("REQ-OPS-010 - idempotency apply -> external secret installation is verified")
    void idempotencyCheckShouldVerifySecretApply() throws IOException {
        Path check = requiredSingleFile(
                ANSIBLE_ROOT,
                path -> path.getFileName().toString().matches("(?i).*idempot.*")
        );

        assertThat(read(check))
                .containsPattern(
                        "(?is)apply_log.*?(?:grep|test|assert|verify).*?(?:secret|/etc/gam/secrets)"
                );
    }

    @Test
    @DisplayName("REQ-OPS-002 - idempotency verification -> exact firewall state and no broad rules")
    void idempotencyCheckShouldVerifyExactFirewallState() throws IOException {
        Path check = requiredSingleFile(
                ANSIBLE_ROOT,
                path -> path.getFileName().toString().matches("(?i).*idempot.*")
        );

        assertThat(read(check))
                .containsPattern("(?is)ufw\\s+status\\s+verbose")
                .containsPattern("(?is)Default:\\s*deny\\s*\\(incoming\\)")
                .containsPattern("(?is)80/tcp")
                .containsPattern("(?is)443/tcp")
                .containsPattern("(?is)(?:ssh_allowed_cidrs|firewall_ssh_allowed_cidrs|GAM_SSH_ALLOWED_CIDR)")
                .containsPattern("(?is)(?:grep|test|assert|verify).*?(?:0\\.0\\.0\\.0/0|::/0)");
    }

    @Test
    @DisplayName("REQ-OPS-002 - idempotency firewall probe -> forwards the apply connection context")
    void idempotencyFirewallProbeShouldForwardApplyConnectionArguments() throws IOException {
        Path check = requiredSingleFile(
                ANSIBLE_ROOT,
                path -> path.getFileName().toString().matches("(?i).*idempot.*")
        );
        List<String> invocations = ansibleInvocations(read(check));

        assertThat(invocations).hasSize(1);
        assertThat(invocations.get(0))
                .containsPattern("(?is)(?:\\$@|\\$\\{[^}]*\\[@\\]\\}|--private-key|ansible_ssh_(?:common_args|private_key_file))");
    }

    @Test
    @DisplayName("REQ-OPS-010 - idempotency helper -> rejects flags that invalidate the real apply")
    void idempotencyCheckShouldRejectApplyShapingArgumentsBeforeApply() throws IOException {
        Path check = requiredSingleFile(
                ANSIBLE_ROOT,
                path -> path.getFileName().toString().matches("(?i).*idempot.*")
        );
        String content = read(check);
        int firstApply = content.indexOf("ansible-playbook");

        assertThat(firstApply).isGreaterThanOrEqualTo(0);
        String beforeApply = content.substring(0, firstApply);
        assertThat(beforeApply)
                .containsPattern("(?is)(?:case|if|for|while).*?(?:--check|--skip-tags|--tags)")
                .containsPattern("(?is)(?:exit\\s+1|return\\s+1).*?(?:invalid|unsupported|not allowed|check|tag)");
    }

    private static Path baselinePlaybook() throws IOException {
        return requiredSingleFile(
                PLAYBOOK_DIRECTORY,
                path -> path.getFileName().toString().matches("(?i).*baseline.*\\.ya?ml")
        );
    }

    private static String readRole(String roleName) throws IOException {
        Path role = ROLE_DIRECTORY.resolve(roleName);
        if (!Files.isDirectory(role)) {
            role = findRole(roleName);
        }
        assertThat(role).as("Ansible role %s", roleName).isDirectory();
        try (Stream<Path> files = Files.walk(role)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(ProductionHostBaselineStructuralTest::readUnchecked)
                    .reduce("", String::concat);
        }
    }

    private static Path findRole(String roleName) throws IOException {
        if (!Files.isDirectory(ROLE_DIRECTORY)) {
            return ROLE_DIRECTORY.resolve(roleName);
        }
        try (Stream<Path> roles = Files.list(ROLE_DIRECTORY)) {
            return roles
                    .filter(Files::isDirectory)
                    .filter(path -> normalized(path.getFileName().toString())
                            .equals(normalized(roleName)))
                    .findFirst()
                    .orElse(ROLE_DIRECTORY.resolve(roleName));
        }
    }

    private static Set<String> roleNames() throws IOException {
        assertThat(ROLE_DIRECTORY).as("Ansible roles directory").isDirectory();
        try (Stream<Path> roles = Files.list(ROLE_DIRECTORY)) {
            return roles
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .map(ProductionHostBaselineStructuralTest::normalized)
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private static Path requiredSingleFile(Path directory, Predicate<Path> predicate)
            throws IOException {
        assertThat(directory).as("Ansible directory").isDirectory();
        try (Stream<Path> files = Files.walk(directory)) {
            List<Path> matches = files
                    .filter(Files::isRegularFile)
                    .filter(predicate)
                    .toList();
            assertThat(matches).as("matching files below %s", directory).hasSize(1);
            return matches.get(0);
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String gitCheckIgnore(String path, boolean includeNonMatching)
            throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of("git", "check-ignore", "-v"));
        if (includeNonMatching) {
            command.add("-n");
        }
        command.add("--no-index");
        command.add(path);

        Process git = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = git.waitFor();

        assertThat(exitCode).as("git check-ignore exit code for %s: %s", path, output).isZero();
        return output;
    }

    private static String readUnchecked(Path path) {
        try {
            return read(path);
        } catch (IOException exception) {
            throw new AssertionError("Could not read Ansible file " + path, exception);
        }
    }

    private static int count(String content, String expression) {
        return (int) Pattern.compile(expression).matcher(content).results().count();
    }

    private static boolean matchesPattern(String content, String expression) {
        return Pattern.compile(expression).matcher(content).find();
    }

    private static List<String> matches(String content, String expression) {
        return Pattern.compile(expression)
                .matcher(content)
                .results()
                .map(match -> match.group(1))
                .toList();
    }

    private static List<String> playbookInvocations(String content) {
        return Pattern.compile(
                        "(?is)\\bansible-playbook\\b.*?(?=\\n\\s*if\\s+!\\s+ansible-playbook\\b|\\z)"
                )
                .matcher(content)
                .results()
                .map(match -> match.group())
                .toList();
    }

    private static List<String> ansibleInvocations(String content) {
        return Pattern.compile(
                        "(?is)\\bansible\\s+production\\b.*?(?=\\n\\s*if\\s+!\\s+ansible-playbook\\b|\\z)"
                )
                .matcher(content)
                .results()
                .map(match -> match.group())
                .toList();
    }

    private static String normalized(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
