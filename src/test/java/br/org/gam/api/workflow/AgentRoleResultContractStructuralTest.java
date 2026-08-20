package br.org.gam.api.workflow;

import br.org.gam.api.testing.annotation.StructuralTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structure - Agent role-result contract")
class AgentRoleResultContractStructuralTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path REQUIREMENT = Path.of(
            "docs", "requirements", "platform", "agent-orchestration-workflow.md"
    );
    private static final Path THIS_TEST = Path.of(
            "src", "test", "java", "br", "org", "gam", "api", "workflow",
            "AgentRoleResultContractStructuralTest.java"
    );
    private static final Path PRODUCTION_ARTIFACT = Path.of(
            "src", "main", "java", "br", "org", "gam", "api", "GamApiApplication.java"
    );

    @ParameterizedTest(name = "{0}")
    @MethodSource("legalRoleResults")
    @DisplayName("REQ-AGENT-005/006/007 - every legal role and phase result -> accepted by the canonical schema")
    void everyLegalRoleAndPhaseResultShouldValidate(String scenario, ObjectNode result) throws Exception {
        Set<ValidationMessage> errors = contractSchema().validate(result);

        assertThat(errors)
                .as("%s should satisfy gam-role-result/v1", scenario)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRoleResults")
    @DisplayName("REQ-AGENT-006/007 - known mechanical result defects -> rejected by the canonical schema")
    void knownMechanicalResultDefectsShouldBeRejected(String scenario, ObjectNode result) throws Exception {
        Set<ValidationMessage> errors = contractSchema().validate(result);

        assertThat(errors)
                .as("%s must be rejected", scenario)
                .isNotEmpty();
    }

    @Test
    @DisplayName("REQ-AGENT-005/006 - canonical v1 contract -> one human-status field and no legacy result vocabulary")
    void canonicalContractShouldReplaceTheDefectiveDefinitionInPlace() throws Exception {
        String contract = Files.readString(canonicalContractPath());

        assertThat(contract)
                .contains("gam-role-result/v1", "human_intervention_required");
    }

    @Test
    @DisplayName("REQ-AGENT-005/006/007 - schema projections -> exact outcomes for every role and phase")
    void canonicalSchemaShouldDefineEveryExactRoleAndPhaseProjection() throws Exception {
        Map<RolePhase, Set<String>> projections = canonicalOutcomeProjections();

        assertThat(projections).containsExactlyInAnyOrderEntriesOf(Map.of(
                new RolePhase("agent_t", "t_initial"), Set.of(
                        "expected_red_confirmed",
                        "requirement_ambiguity",
                        "test_authority_conflict",
                        "no_valid_test_seam",
                        "verification_blocker",
                        "role_mismatch"
                ),
                new RolePhase("agent_t", "t_expanded"), Set.of(
                        "production_issue_exposed",
                        "td_loop_complete",
                        "requirement_ambiguity",
                        "test_authority_conflict",
                        "no_valid_test_seam",
                        "verification_blocker",
                        "role_mismatch"
                ),
                new RolePhase("agent_d", "d_initial"), Set.of(
                        "initial_implementation_satisfies_tests",
                        "test_authority_conflict",
                        "architecture_decision_required",
                        "verification_blocker",
                        "role_mismatch"
                ),
                new RolePhase("agent_d", "d_correction"), Set.of(
                        "production_issue_fixed",
                        "test_authority_conflict",
                        "architecture_decision_required",
                        "verification_blocker",
                        "role_mismatch"
                ),
                new RolePhase("agent_r", "r_review"), Set.of(
                        "no_actionable_findings",
                        "test_design_issue_found",
                        "implementation_issue_found",
                        "requirement_or_domain_ambiguity",
                        "architecture_decision_required",
                        "scope_decision_required",
                        "permission_blocker",
                        "verification_blocker",
                        "role_mismatch"
                )
        ));
    }

    private static Stream<Arguments> legalRoleResults() {
        List<Arguments> results = new ArrayList<>();

        results.add(valid("Agent T initial expected red", expectedRed("t_initial", "expected_red_confirmed")));
        addEscalations(results, "agent_t", "t_initial", List.of(
                "requirement_ambiguity",
                "test_authority_conflict",
                "no_valid_test_seam",
                "verification_blocker",
                "role_mismatch"
        ));

        results.add(valid("Agent T expanded production issue", expectedRed("t_expanded", "production_issue_exposed")));
        ObjectNode complete = success("agent_t", "t_expanded", "td_loop_complete");
        complete.withObject("details").put("completion_criteria_satisfied", true);
        results.add(valid("Agent T expanded loop complete", complete));
        addEscalations(results, "agent_t", "t_expanded", List.of(
                "requirement_ambiguity",
                "test_authority_conflict",
                "no_valid_test_seam",
                "verification_blocker",
                "role_mismatch"
        ));

        results.add(valid(
                "Agent D initial implementation satisfies tests",
                implementationSuccess("d_initial", "initial_implementation_satisfies_tests")
        ));
        addEscalations(results, "agent_d", "d_initial", List.of(
                "test_authority_conflict",
                "architecture_decision_required",
                "verification_blocker",
                "role_mismatch"
        ));

        results.add(valid(
                "Agent D correction fixes issue",
                implementationSuccess("d_correction", "production_issue_fixed")
        ));
        addEscalations(results, "agent_d", "d_correction", List.of(
                "test_authority_conflict",
                "architecture_decision_required",
                "verification_blocker",
                "role_mismatch"
        ));

        results.add(valid("Agent R no findings", reviewResult("no_actionable_findings", null)));
        results.add(valid(
                "Agent R test-design finding",
                reviewResult("test_design_issue_found", "missing_or_misleading_coverage")
        ));
        ObjectNode mixedTestAndImplementationFindings = reviewResult(
                "test_design_issue_found",
                "defect_without_adequate_failing_coverage"
        );
        addFinding(
                mixedTestAndImplementationFindings,
                "unambiguous_implementation_issue",
                "Implementation also requires correction"
        );
        results.add(valid(
                "Agent R mixed test-design and implementation findings use test-design precedence",
                mixedTestAndImplementationFindings
        ));
        ObjectNode testFindingWithVerificationRisk = reviewResult(
                "test_design_issue_found",
                "missing_or_misleading_coverage"
        );
        addFinding(
                testFindingWithVerificationRisk,
                "verification_concern",
                "Non-blocking verification evidence remains uncertain"
        );
        testFindingWithVerificationRisk.withArray("risks").add("Non-blocking verification concern");
        results.add(valid(
                "Agent R non-blocking verification concern remains a risk beside test-design finding",
                testFindingWithVerificationRisk
        ));
        results.add(valid(
                "Agent R implementation finding",
                reviewResult("implementation_issue_found", "unambiguous_implementation_issue")
        ));
        addEscalations(results, "agent_r", "r_review", List.of(
                "requirement_or_domain_ambiguity",
                "architecture_decision_required",
                "scope_decision_required",
                "permission_blocker",
                "verification_blocker",
                "role_mismatch"
        ));

        return results.stream();
    }

    private static Stream<Arguments> invalidRoleResults() {
        List<Arguments> results = new ArrayList<>();

        ObjectNode roleIncompatible = expectedRed("t_initial", "expected_red_confirmed");
        roleIncompatible.put("outcome", "no_actionable_findings");
        roleIncompatible.withObject("details").remove("expected_red_signal");
        roleIncompatible.withObject("details").putArray("findings");
        results.add(invalid("role-incompatible outcome", roleIncompatible));

        ObjectNode missingVerification = expectedRed("t_initial", "expected_red_confirmed");
        missingVerification.remove("verification");
        results.add(invalid("missing required verification field", missingVerification));

        ObjectNode inconsistentIntervention = expectedRed("t_initial", "expected_red_confirmed");
        inconsistentIntervention.put("human_intervention_required", true);
        results.add(invalid("inconsistent human-intervention flag", inconsistentIntervention));

        ObjectNode invalidArtifact = expectedRed("t_initial", "expected_red_confirmed");
        ((ObjectNode) invalidArtifact.withArray("artifacts").get(0)).put("path", "../outside.md");
        results.add(invalid("invalid artifact reference", invalidArtifact));

        ObjectNode legacyField = reviewResult("no_actionable_findings", null);
        legacyField.put("human_decision_required", false);
        results.add(invalid("legacy human-decision field", legacyField));

        ObjectNode legacyOutcome = escalation("agent_r", "r_review", "verification_blocker");
        legacyOutcome.put("outcome", "human_decision_required");
        results.add(invalid("legacy human-decision outcome", legacyOutcome));

        ObjectNode missingExpectedRedDetail = expectedRed("t_initial", "expected_red_confirmed");
        missingExpectedRedDetail.withObject("details").remove("expected_red_signal");
        results.add(invalid("missing expected-red detail", missingExpectedRedDetail));

        ObjectNode incompleteLoop = success("agent_t", "t_expanded", "td_loop_complete");
        incompleteLoop.withObject("details").put("completion_criteria_satisfied", false);
        results.add(invalid("false T-D loop completion detail", incompleteLoop));

        ObjectNode missingFindings = reviewResult("test_design_issue_found", "missing_or_misleading_coverage");
        missingFindings.withObject("details").remove("findings");
        results.add(invalid("missing Agent R findings", missingFindings));

        ObjectNode missingBlocker = escalation("agent_r", "r_review", "permission_blocker");
        missingBlocker.withArray("blockers").removeAll();
        results.add(invalid("escalation without exact blocker", missingBlocker));

        ObjectNode vagueBlocker = escalation("agent_r", "r_review", "permission_blocker");
        vagueBlocker.withArray("blockers").removeAll().add("Developer help is needed");
        results.add(invalid("escalation blocker without evidence or exact unresolved decision", vagueBlocker));

        ObjectNode normalRequirementGap = reviewResult(
                "test_design_issue_found",
                "missing_or_misleading_coverage"
        );
        addFinding(
                normalRequirementGap,
                "requirement_domain_scope_architecture_gap",
                "Substantive requirement gap cannot be a normal routed finding"
        );
        results.add(invalid("substantive review gap reported as a normal finding", normalRequirementGap));

        for (String reference : List.of(
                "/absolute/path.md",
                "C:/absolute/path.md",
                "docs\\requirements\\file.md",
                "docs//requirements/file.md"
        )) {
            ObjectNode malformedArtifact = expectedRed("t_initial", "expected_red_confirmed");
            ((ObjectNode) malformedArtifact.withArray("artifacts").get(0)).put("path", reference);
            results.add(invalid("non-normalized artifact reference " + reference, malformedArtifact));
        }

        return results.stream();
    }

    private static void addEscalations(
            List<Arguments> results,
            String role,
            String phase,
            List<String> outcomes
    ) {
        outcomes.forEach(outcome -> results.add(valid(
                role + " " + phase + " " + outcome,
                escalation(role, phase, outcome)
        )));
    }

    private static ObjectNode expectedRed(String phase, String outcome) {
        ObjectNode result = success("agent_t", phase, outcome);
        result.withObject("details").put("expected_red_signal", "accepted behavior is not implemented");
        ArrayNode artifacts = result.withArray("artifacts");
        artifacts.add(artifact(THIS_TEST, "test", "created"));
        ArrayNode verification = result.withArray("verification");
        verification.removeAll();
        verification.add(verification("failed", "expected assertion failure", "expected_red"));
        return result;
    }

    private static ObjectNode implementationSuccess(String phase, String outcome) {
        ObjectNode result = success("agent_d", phase, outcome);
        result.withArray("artifacts").add(artifact(PRODUCTION_ARTIFACT, "production", "modified"));
        return result;
    }

    private static ObjectNode reviewResult(String outcome, String classification) {
        ObjectNode result = success("agent_r", "r_review", outcome);
        result.withObject("details").putArray("findings");
        if (classification != null) {
            addFinding(result, classification, "Requirement conformance is incomplete");
        }
        return result;
    }

    private static void addFinding(ObjectNode result, String classification, String summary) {
        ObjectNode finding = result.withObject("details").withArray("findings").addObject();
        finding.put("classification", classification);
        finding.putArray("evidence").add(REQUIREMENT.toString().replace('\\', '/'));
        finding.putArray("affected_artifacts").add(THIS_TEST.toString().replace('\\', '/'));
        finding.put("summary", summary);
    }

    private static ObjectNode success(String role, String phase, String outcome) {
        ObjectNode result = common(role, phase, outcome);
        result.put("human_intervention_required", false);
        result.withArray("verification").add(verification("passed", "focused verification passed", "pass"));
        return result;
    }

    private static ObjectNode escalation(String role, String phase, String outcome) {
        ObjectNode result = common(role, phase, outcome);
        result.put("human_intervention_required", true);
        result.withArray("blockers").add(
                "Evidence: native state cannot satisfy the requirement. "
                        + "Exact unresolved decision: choose the authorized continuation."
        );
        return result;
    }

    private static ObjectNode common(String role, String phase, String outcome) {
        ObjectNode result = JSON.createObjectNode();
        result.put("schema_version", "gam-role-result/v1");
        result.put("workflow_id", "agent-orchestration-reliability-test");
        result.put("role", role);
        result.put("phase", phase);
        result.put("outcome", outcome);
        result.putArray("artifacts");
        result.putArray("authoritative_artifacts").add(REQUIREMENT.toString().replace('\\', '/'));
        result.putArray("verification");
        result.putArray("blockers");
        result.putArray("risks");
        result.putArray("scope_deviations");
        result.putObject("details");
        return result;
    }

    private static ObjectNode artifact(Path path, String kind, String change) {
        ObjectNode artifact = JSON.createObjectNode();
        artifact.put("path", path.toString().replace('\\', '/'));
        artifact.put("kind", kind);
        artifact.put("change", change);
        return artifact;
    }

    private static ObjectNode verification(String status, String observed, String signal) {
        ObjectNode verification = JSON.createObjectNode();
        verification.put("command", "rtk test .\\mvnw.cmd -Dtest=AgentRoleResultContractStructuralTest test");
        verification.put("status", status);
        verification.put("observed", observed);
        verification.put("signal", signal);
        return verification;
    }

    private static Arguments valid(String scenario, ObjectNode result) {
        return Arguments.of(scenario, result);
    }

    private static Arguments invalid(String scenario, ObjectNode result) {
        return Arguments.of(scenario, result);
    }

    private static JsonSchema contractSchema() throws Exception {
        JsonNode schema = JSON.readTree(canonicalContractPath().toFile());
        return JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(schema);
    }

    static Map<RolePhase, Set<String>> canonicalOutcomeProjections() throws Exception {
        JsonNode schema = JSON.readTree(canonicalContractPath().toFile());
        Map<RolePhase, Set<String>> projections = new LinkedHashMap<>();
        collectOutcomeProjections(schema, projections);
        return projections;
    }

    private static void collectOutcomeProjections(
            JsonNode node,
            Map<RolePhase, Set<String>> projections
    ) {
        JsonNode properties = node.path("properties");
        String role = properties.path("role").path("const").textValue();
        String phase = properties.path("phase").path("const").textValue();
        JsonNode outcome = properties.path("outcome");
        if (role != null && phase != null && !outcome.isMissingNode()) {
            Set<String> allowed = projections.computeIfAbsent(
                    new RolePhase(role, phase),
                    ignored -> new TreeSet<>()
            );
            if (outcome.has("const")) {
                allowed.add(outcome.path("const").textValue());
            }
            outcome.path("enum").forEach(value -> allowed.add(value.textValue()));
        }

        node.elements().forEachRemaining(child -> collectOutcomeProjections(child, projections));
    }

    private static Path canonicalContractPath() throws IOException {
        List<Path> candidates;
        try (Stream<Path> paths = Files.walk(Path.of(".agents"))) {
            candidates = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".schema.json"))
                    .filter(AgentRoleResultContractStructuralTest::declaresRoleResultV1)
                    .toList();
        }

        assertThat(candidates)
                .as("exactly one repository-owned gam-role-result/v1 JSON Schema must be canonical")
                .hasSize(1);
        return candidates.getFirst();
    }

    private static boolean declaresRoleResultV1(Path path) {
        try {
            return Files.readString(path).contains("gam-role-result/v1");
        } catch (IOException exception) {
            throw new AssertionError("Could not inspect candidate contract " + path, exception);
        }
    }

    record RolePhase(String role, String phase) {
    }
}
