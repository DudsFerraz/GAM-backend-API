package br.org.gam.api.production;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@StructuralTest
@DisplayName("Structure - production backup, recovery, AWS, and monitoring contracts")
class ProductionBackupAwsStructuralTest {

    private static final Path OPERATIONS = Path.of("operations");
    private static final Path ANSIBLE = OPERATIONS.resolve("ansible");
    private static final Path BACKUP = OPERATIONS.resolve("recovery").resolve("backup");
    private static final Path RESTORE = OPERATIONS.resolve("recovery").resolve("restore");
    private static final Path RESTORATION_VERIFICATION = OPERATIONS
            .resolve("recovery")
            .resolve("verify-restoration");
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![\\d.])((?:\\d{1,3}\\.){3}\\d{1,3})(?:/\\d{1,2})?(?![\\d.])"
    );
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".7z", ".bin", ".class", ".dat", ".dll", ".dylib", ".exe", ".gz", ".jar",
            ".pyd", ".pyo", ".pyc", ".so", ".tar", ".whl", ".zip"
    );

    @Test
    @DisplayName("ADR-0024 and REQ-OPS-010 - versioned operations -> Ansible and isolated recovery seams exist")
    void versionedOperationsShouldContainAnsibleAndRecoverySeams() throws IOException {
        assertThat(Files.isDirectory(ANSIBLE)).as("versioned Ansible root").isTrue();
        assertThat(Files.isDirectory(BACKUP)).as("backup recovery seam").isTrue();
        assertThat(Files.isDirectory(RESTORE)).as("restore recovery seam").isTrue();
        assertThat(Files.isDirectory(RESTORATION_VERIFICATION))
                .as("restoration verification seam")
                .isTrue();

        assertThat(machineFiles(OPERATIONS))
                .as("versioned machine-readable operations")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Structural file selection -> ignores generated caches and binaries but retains machine-readable operations")
    void machineFileSelectionShouldIgnoreGeneratedCachesAndBinaries() throws IOException {
        List<Path> files = machineFiles(OPERATIONS);

        assertThat(files)
                .as("generated Python caches must not be scanned as text")
                .noneMatch(path -> path.toString().replace('\\', '/').toLowerCase(Locale.ROOT)
                        .contains("/__pycache__/"))
                .noneMatch(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pyc"));
        assertThat(files)
                .as("versioned machine-readable operations")
                .contains(
                        ANSIBLE.resolve("aws-resources.yml"),
                        ANSIBLE.resolve("backup_monitor.py"),
                        BACKUP.resolve("backup.sh")
                );
    }

    @Test
    @DisplayName("REQ-BACKUP-001/002/005 - backup job -> complete validated encrypted artifact with the approved data boundary")
    void backupJobShouldCreateAndValidateTheCompleteEncryptedArtifact() throws IOException {
        String backup = textUnder(BACKUP);

        containsAll(backup, "pg_dump", "pg_dumpall", "manifest", "sha256", "age");
        containsAny(backup, "--format=custom", "-fc", "custom-format", "format: custom");
        containsAny(backup, "refresh_tokens", "refresh-tokens");
        containsAny(backup, "exclude-table-data=refresh_tokens", "exclude-table-data", "exclude: refresh_tokens");
        containsAny(backup, "roles-only", "no-role-passwords", "password-free", "role export");
        containsAny(backup, "aws s3", "s3api put-object", "put_object", "upload");
        containsAny(backup, "head-object", "head_object", "get-object-attributes", "metadata");
        containsAny(backup, "checksum", "sha256sum", "digest");
        containsAny(backup, "staging", "cleanup", "trap", "remove");

        assertThat(backup)
                .as("immutable recovery-object key layout")
                .contains("production/postgresql")
                .containsPattern("(?is)(yyyy|%y).{0,80}(mm|%m).{0,80}(dd|%d)")
                .containsPattern("(?i)timestamp|utc")
                .containsPattern("(?i)classification|daily|weekly|monthly");
        assertThat(backup)
                .as("two independent public encryption recipients")
                .containsPattern("(?is)(developer|primary).{0,180}(recipient|public key)")
                .containsPattern("(?is)(client|emergency).{0,180}(recipient|public key)");
    }

    @Test
    @DisplayName("REQ-BACKUP-001/003/004 and ADR-0025 - daily timer -> one classified recovery point with class-specific Compliance retention")
    void backupScheduleAndRetentionShouldPreserveRecoveryPointCounts() throws IOException {
        String operations = operationalText(OPERATIONS);

        containsAll(operations, "03:15", "america/sao_paulo", "daily", "weekly", "monthly");
        assertThat(operations)
                .containsPattern("(?is)(daily|day).{0,120}31|31.{0,120}(daily|day)")
                .containsPattern("(?is)(weekly|week).{0,120}85|85.{0,120}(weekly|week)")
                .containsPattern("(?is)(monthly|month).{0,120}370|370.{0,120}(monthly|month)");
        containsAny(operations, "monday", "day-of-week", "weekday");
        containsAny(operations, "first successful", "first-of-month", "first day of the month");
        containsAny(operations, "longest", "overlap", "one object", "single object");
        containsAny(operations, "catch up", "catch-up", "persistent", "onboot", "after reboot");

        assertThat(operations)
                .as("classification lifecycle")
                .containsPattern("(?i)standard-ia")
                .containsPattern("(?i)glacier.{0,80}flexible|flexible.{0,80}retrieval")
                .containsPattern("(?is)(standard-ia).{0,100}30|30.{0,100}(standard-ia)")
                .containsPattern("(?is)(glacier|flexible).{0,100}90|90.{0,100}(glacier|flexible)")
                .containsPattern("(?is)multipart.{0,100}7|7.{0,100}multipart");
    }

    @Test
    @DisplayName("REQ-BACKUP-003 - overlapping Monday and first-of-month success -> closes both pending classifications on one object")
    void overlappingWeeklyAndMonthlySuccessShouldCloseBothPendingClassifications() throws IOException {
        String backup = textUnder(BACKUP);

        assertThat(backup)
                .as("monthly classification branch must resolve the weekly pending marker too")
                .containsPattern(
                        "(?is)if \\[\\[ \\\"\\$CLASSIFICATION\\\" == monthly \\]\\]"
                                + ".*?\\$WEEKLY_PENDING.*?elif \\[\\[ \\\"\\$CLASSIFICATION\\\" == weekly"
                );
    }

    @Test
    @DisplayName("REQ-BACKUP-005 and REQ-OPS-004 - versioned operations -> no private recovery key material is stored")
    void versionedOperationsShouldNotStorePrivateRecoveryKeyMaterial() throws IOException {
        String operations = operationalText(OPERATIONS);

        assertThat(operations)
                .doesNotContain("age-secret-key-")
                .doesNotContain("begin private key")
                .doesNotContain("begin openssh private key")
                .doesNotContain("begin rsa private key")
                .doesNotContain("begin ec private key");
    }

    @Test
    @DisplayName("REQ-BACKUP-006 - backup writer policy -> source address is parameterized and destructive AWS privileges are absent")
    void backupWriterShouldUseAParameterizedVpsSourceAddressWithoutDestructivePrivileges() throws IOException {
        String writer = textOfFilesContaining(ANSIBLE, "backup-writer", "backup_writer");

        containsAny(writer, "gam-production-backup-writer", "backup-writer");
        containsAny(writer, "s3:putobject", "putobject", "put_object");
        containsAny(writer, "s3:headobject", "headobject", "getobjectattributes", "head_object");
        containsAny(writer, "aws:sourceip", "source_ip", "public_source_ip", "vps_public");
        containsAny(writer, "90 days", "90-day", "90d");
        containsAny(writer, "rotate", "rotation");
        assertThat(writer)
                .doesNotContain("s3:deleteobject", "deleteobject", "delete_object")
                .doesNotContain("bypassgovernanceretention", "bypass_governance_retention")
                .doesNotContain("iam:*", "iam:put", "budgets:");
    }

    @Test
    @DisplayName("REQ-BACKUP-006 - Ansible and AWS policy -> VPS public source IP remains an input, never a concrete public literal")
    void vpsPublicSourceIpShouldRemainParameterized() throws IOException {
        String operations = read(ANSIBLE.resolve("group_vars").resolve("production.yml"))
                + read(ANSIBLE.resolve("templates").resolve("backup-writer-policy.json.j2"));

        containsAny(operations, "source_ip", "source-ip", "public_source_ip", "vps_public_ip", "vps-public-ip");
        assertThat(publicIpv4Literals(operations))
                .as("concrete public VPS source addresses")
                .isEmpty();
    }

    @Test
    @DisplayName("REQ-BACKUP-007/012 and ADR-0025 - AWS resources -> Brazilian private immutable bucket and cost governance")
    void awsResourcesShouldProvideBrazilianImmutableStorageAndCostGovernance() throws IOException {
        String operations = operationalText(ANSIBLE);

        containsAll(
                operations,
                "sa-east-1",
                "gam-production-backups",
                "block public",
                "versioning",
                "object lock",
                "compliance",
                "bucket-owner-enforced"
        );
        containsAny(operations, "sse-s3", "aes256", "server-side encryption");
        containsAny(operations, "https-only", "aws:securetransport", "secure transport");
        assertThat(operations)
                .doesNotContain("cross-region replication", "cross_region_replication", "replication_configuration")
                .containsPattern("(?is)project.{0,80}gam")
                .containsPattern("(?is)environment.{0,80}production")
                .containsPattern("(?is)purpose.{0,80}backup");

        containsAll(operations, "5", "10", "25", "cost anomaly");
        containsAny(operations, "actual-or-forecast", "forecast", "actual_or_forecast");
        containsAny(operations, "5 usd", "us$5", "500 cents", "impact: 5");
        assertThat(operations)
                .as("cost alerts must not weaken recovery")
                .doesNotContain("disable backup", "stop backup", "delete recovery point", "delete retained object");
    }

    @Test
    @DisplayName("REQ-BACKUP-009 and ADR-0025 - CloudTrail -> separate immutable audit bucket observes backup access without recursive delivery events")
    void auditResourcesShouldRecordImmutableBackupAccessSeparately() throws IOException {
        String operations = operationalText(ANSIBLE);

        containsAll(operations, "cloudtrail", "log file integrity", "data events", "read", "write");
        containsAny(operations, "upload", "putobject", "download", "getobject", "retention", "tagging");
        containsAll(operations, "gam-production-backup-audit", "400", "compliance", "sse-s3");
        containsAny(operations, "exclude", "excluded", "not log", "does not log", "audit destination");
        containsAny(operations, "block public", "public access block", "versioning");
    }

    @Test
    @DisplayName("REQ-BACKUP-008 and ADR-0025 - independent AWS monitor -> validates object metadata and escalates unresolved failures")
    void independentAwsMonitorShouldValidateAndEscalateBackupFailures() throws IOException {
        String operations = operationalText(OPERATIONS);

        containsAll(operations, "eventbridge", "lambda", "04:30", "12:00", "america/sao_paulo");
        containsAny(operations, "current local date", "local-date", "local_date", "today");
        containsAny(operations, "exists", "head-object", "head_object", "object metadata");
        containsAny(operations, "nonzero", "non-zero", "content-length", "size");
        containsAny(operations, "checksum", "digest");
        containsAny(operations, "encrypted", "encryption", "content-encoding");
        containsAny(operations, "classification", "retention", "retain-until", "object-lock");
        containsAny(operations, "sns", "developer", "immediate alert");
        containsAny(operations, "unresolved", "escalat", "client custodian");
        containsAny(operations, "recovery notice", "retry succeeded", "later retry");
        containsAny(operations, "cloudwatch", "alarm", "monitor failure");
    }

    @Test
    @DisplayName("REQ-BACKUP-010/011 - isolated restoration -> invalidates sessions, verifies representative data, and destroys temporary plaintext")
    void restorationProcedureShouldBeIsolatedAndSecure() throws IOException {
        String restore = textUnder(RESTORE);

        containsAny(restore, "isolated", "private network", "no public traffic", "public traffic disabled");
        containsAny(restore, "pg_restore", "restore");
        containsAny(restore, "refresh_tokens", "refresh-tokens");
        containsAny(restore, "truncate", "delete from", "empty", "excluded");
        assertThat(restore)
                .containsPattern("(?is)jwt.{0,120}(rotate|secret)|(?:rotate|rotat).{0,120}jwt")
                .containsPattern("(?i)sign.?in|authenticate|re-auth");
        containsAny(restore, "plaintext", "decrypted", "temporary archive", "staging");
        containsAny(restore, "destroy", "securely remove", "cleanup", "rm ", "shred");
    }

    @Test
    @DisplayName("REQ-BACKUP-010/011 - restoration verification -> records non-sensitive evidence and material-change triggers")
    void restorationVerificationShouldRecordEvidenceWithoutPlaintextData() throws IOException {
        String verification = textUnder(RESTORATION_VERIFICATION);

        containsAll(verification, "annual", "postgresql", "major", "backup format", "encryption scheme");
        containsAny(verification, "selected recovery point", "selected_recovery_point", "recovery point");
        containsAny(verification, "checksum", "digest");
        containsAny(verification, "duration", "elapsed");
        containsAny(verification, "structural", "schema", "invariant", "representative");
        containsAny(verification, "attachment", "sampling", "sample checksum");
        containsAny(verification, "corrective", "remediation", "action");
        containsAny(verification, "no plaintext", "without plaintext", "sensitive data", "personal data");
    }

    @Test
    @DisplayName("REQ-BACKUP-011 - restoration evidence -> representative application validation is non-tautological")
    void restorationEvidenceShouldRejectTautologicalRepresentativeChecks() throws IOException {
        String verification = textUnder(RESTORATION_VERIFICATION);

        assertThat(verification)
                .as("representative restoration evidence must prove an application or data condition")
                .doesNotContain("count(*) >= 0", "count(*)>=0")
                .containsPattern("(?is)(representative|application).{0,240}(exists|is not null|>\\s*0|=\\s*1)");
    }

    @Test
    @DisplayName("REQ-OPS-007/014 and ADR-0029 - Better Stack -> provider-supported availability, TLS, and metrics-only host monitoring")
    void betterStackMonitoringShouldRemainExternalAndMetricsOnly() throws IOException {
        String operations = operationalText(OPERATIONS);

        containsAll(operations, "better stack", "/api/health", "200", "up", "tls");
        containsAny(operations, "five minutes", "5 minutes", "every: 5", "interval: 300", "300 seconds");
        containsAny(operations, "600", "600 seconds", "confirmation_period: 600", "ten-minute");
        containsAny(operations, "email", "mobile push", "push");
        assertThat(operations)
                .containsPattern("(?is)(certificate|tls|expiry).{0,100}30|30.{0,100}(certificate|tls|expiry)");
        containsAny(operations, "invalid", "expired", "hostname-mismatched", "unverifiable");
        containsAny(operations, "metrics-only", "metrics only", "host metrics");
        containsAll(operations, "filesystem", "inode", "network", "container", "restart");
        assertThat(operations)
                .containsPattern("(?is)(filesystem|disk).{0,120}80")
                .containsPattern("(?is)(filesystem|disk).{0,120}90")
                .doesNotContain("request-body export", "request body export", "distributed-trace export");
        assertThat(read(OPERATIONS.resolve("ansible").resolve("templates").resolve("better-stack-monitoring.yml.j2")))
                .doesNotContain("three consecutive", "3 consecutive", "failure threshold: 3", "threshold: 3");
    }

    private static String textUnder(Path root) throws IOException {
        assertThat(Files.isDirectory(root)).as("versioned operations path %s", root).isTrue();
        List<Path> files = machineFiles(root);
        assertThat(files).as("machine-readable files below %s", root).isNotEmpty();
        return files.stream()
                .map(ProductionBackupAwsStructuralTest::read)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private static String operationalText(Path root) throws IOException {
        return textUnder(root);
    }

    private static List<Path> machineFiles(Path root) throws IOException {
        assertThat(Files.isDirectory(root)).as("versioned operations path %s", root).isTrue();
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(ProductionBackupAwsStructuralTest::isMachineFile)
                    .sorted()
                    .toList();
        }
    }

    private static boolean isMachineFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return !hasPathSegment(path, "__pycache__")
                && BINARY_EXTENSIONS.stream().noneMatch(filename::endsWith)
                && !filename.endsWith(".md")
                && !filename.equals("readme");
    }

    private static boolean hasPathSegment(Path path, String expectedSegment) {
        for (Path segment : path) {
            if (segment.toString().equalsIgnoreCase(expectedSegment)) {
                return true;
            }
        }
        return false;
    }

    private static String textOfFilesContaining(Path root, String... needles) throws IOException {
        List<Path> matching = machineFiles(root).stream()
                .filter(path -> {
                    String content = read(path).toLowerCase(Locale.ROOT);
                    for (String needle : needles) {
                        if (content.contains(needle.toLowerCase(Locale.ROOT))) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
        assertThat(matching).as("Ansible backup-writer policy files").isNotEmpty();
        return matching.stream()
                .map(ProductionBackupAwsStructuralTest::read)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + "\n" + right);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path).replace('\\', '/');
        } catch (IOException exception) {
            throw new AssertionError("Could not read versioned operations file " + path, exception);
        }
    }

    private static void containsAll(String text, String... values) {
        for (String value : values) {
            assertThat(text).as("operations contract marker %s", value).contains(value.toLowerCase(Locale.ROOT));
        }
    }

    private static void containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value.toLowerCase(Locale.ROOT))) {
                return;
            }
        }
        assertThat(text).as("one of the operations contract markers %s", List.of(values)).contains(values[0]);
    }

    private static List<String> publicIpv4Literals(String text) {
        List<String> publicLiterals = new ArrayList<>();
        Matcher matcher = IPV4.matcher(text);
        while (matcher.find()) {
            String literal = matcher.group(1);
            String[] octets = literal.split("\\.");
            int first = Integer.parseInt(octets[0]);
            int second = Integer.parseInt(octets[1]);
            int third = Integer.parseInt(octets[2]);

            if (first > 255 || second > 255 || third > 255 || Integer.parseInt(octets[3]) > 255) {
                continue;
            }
            boolean nonPublic = first == 0
                    || first == 10
                    || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 192 && second == 0 && third == 0)
                    || (first == 198 && (second == 18 || second == 19 || second == 51))
                    || (first == 203 && second == 0 && third == 113)
                    || first >= 224;
            if (!nonPublic) {
                publicLiterals.add(literal);
            }
        }
        return publicLiterals;
    }
}
