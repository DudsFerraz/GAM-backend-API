package br.org.gam.api.workflow;

import br.org.gam.api.testing.annotation.StructuralTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structure - Agent orchestration reliability")
class AgentOrchestrationSkillStructuralTest {

    private static final Path ORCHESTRATION = Path.of(
            ".agents", "skills", "gam-orchestration", "SKILL.md"
    );
    private static final Path WORKFLOW = Path.of(
            ".agents", "skills", "gam-agent-workflow", "SKILL.md"
    );
    private static final Path RESULT_GUIDE = Path.of(
            ".agents", "skills", "gam-agent-workflow", "references", "role-result-contract.md"
    );
    private static final Path AGENT_INSTRUCTIONS = Path.of("AGENTS.md");
    private static final Path TESTING_GUIDELINES = Path.of(
            "docs", "software-guidelines", "testing.md"
    );
    private static final Path HANDOFF = Path.of(
            ".agents", "skills", "gam-agent-handoff", "SKILL.md"
    );
    private static final Path TRACKED_CONFIG = Path.of(".codex.example", "config.toml");
    private static final Path ACTIVE_CONFIG = Path.of(".codex", "config.toml");
    private static final List<Path> RESULT_CONSUMERS = List.of(
            ORCHESTRATION,
            WORKFLOW,
            RESULT_GUIDE,
            HANDOFF,
            Path.of(".codex", "agents", "gam-agent-t.toml"),
            Path.of(".codex", "agents", "gam-agent-d.toml"),
            Path.of(".codex", "agents", "gam-agent-r.toml"),
            Path.of(".codex.example", "agents", "gam-agent-t.toml"),
            Path.of(".codex.example", "agents", "gam-agent-d.toml"),
            Path.of(".codex.example", "agents", "gam-agent-r.toml")
    );

    @Test
    @DisplayName("REQ-AGENT-001/002/003 - accepted requirement and explicit obsolete test -> automatic authoritative routing")
    void requirementsAndExplicitObsoleteTestsShouldDriveRoutineRouting() throws Exception {
        String orchestration = normalized(ORCHESTRATION);

        assertThat(orchestration)
                .contains("accepted requirement", "lower-priority artifact", "preserve the artifact mismatch")
                .contains("explicitly changes", "obsolete test", "agent t", "without developer approval")
                .contains("requirement silence", "escalat")
                .contains("substantive blocker");
    }

    @Test
    @DisplayName("REQ-AGENT-002 - project test authority -> accepted-requirement exception is consistent without weakening safeguards")
    void projectTestAuthorityShouldConsistentlyAllowExplicitRequirementDirectedCorrection() throws Exception {
        for (Path authority : List.of(AGENT_INSTRUCTIONS, TESTING_GUIDELINES)) {
            assertThat(normalized(authority))
                    .as("test-preservation authority %s", authority)
                    .contains(
                            "accepted requirement",
                            "explicitly changes",
                            "agent t",
                            "without repeated developer approval",
                            "preserve or strengthen",
                            "requirement silence",
                            "material reduction"
                    );
        }
    }

    @Test
    @DisplayName("REQ-AGENT-005/006/007 - contract consumers -> canonical schema projection without duplicated legacy vocabulary")
    void resultConsumersShouldProjectTheCanonicalContractWithoutDrift() throws Exception {
        for (Path consumer : RESULT_CONSUMERS) {
            String text = normalized(consumer);
            assertThat(text)
                    .as("contract consumer %s", consumer)
                    .contains("gam-role-result/v1", "human_intervention_required");
        }

        assertThat(normalized(RESULT_GUIDE))
                .doesNotContain("| agent r | r_review | `human_decision_required`");
        assertThat(normalized(ORCHESTRATION))
                .contains("contract_projection", "role", "phase", "allowed_outcomes", "required_common_fields");
        assertThat(normalized(HANDOFF))
                .contains(
                        "derive `contract_projection` from",
                        "gam-role-result.schema.json",
                        "only the target role and phase's allowed outcomes",
                        "do not independently redefine"
                );
    }

    @Test
    @DisplayName("REQ-AGENT-005/006/007 - explicit consumer projections -> exact schema outcomes without missing, added, or phase-leaked values")
    void explicitConsumerOutcomeProjectionsShouldExactlyMatchTheCanonicalSchema() throws Exception {
        Map<AgentRoleResultContractStructuralTest.RolePhase, Set<String>> canonical =
                AgentRoleResultContractStructuralTest.canonicalOutcomeProjections();

        assertThat(roleResultGuideOutcomeProjections())
                .as("role-result-contract.md outcome projections must exactly match the canonical schema")
                .containsExactlyInAnyOrderEntriesOf(canonical);
    }

    @Test
    @DisplayName("REQ-AGENT-008 - invalid result -> at most two same-thread complete re-emissions before escalation")
    void mechanicalResultDefectsShouldUseBoundedSameRoleReEmission() throws Exception {
        String orchestration = normalized(ORCHESTRATION);

        assertThat(orchestration)
                .contains("re-emit", "same role thread", "at most two", "validation errors", "contract projection")
                .contains("engineering facts", "does not fabricate")
                .contains("both correction attempts", "human_intervention_required");
    }

    @Test
    @DisplayName("REQ-AGENT-006/007 - result artifacts -> normalized, existing, and within reporting-role ownership")
    void resultValidationShouldCheckArtifactExistenceAndRoleOwnership() throws Exception {
        String orchestration = normalized(ORCHESTRATION);

        assertThat(orchestration)
                .contains("created or modified artifact path exists")
                .contains("reporting role's ownership")
                .contains("reject");
    }

    @Test
    @DisplayName("REQ-AGENT-009/015/016 - completed threads -> explicit state and best-effort cleanup without blocking transitions")
    void threadLifecycleShouldBeExplicitAndCleanupBestEffort() throws Exception {
        String orchestration = normalized(ORCHESTRATION);

        assertThat(orchestration)
                .contains(
                        "native identity",
                        "current phase",
                        "resumability",
                        "latest native status",
                        "lifecycle state"
                )
                .contains("active", "completed", "interrupted", "confirmed closed")
                .contains("completed or interrupted", "remain open", "closure is confirmed")
                .contains("disappearance from an active list", "cleanup request")
                .contains(
                        "supported close operation",
                        "attempt",
                        "retained completed",
                        "exhausted result-correction attempts",
                        "without blocking the transition",
                        "fresh agent r"
                )
                .contains("interruption is not closure")
                .contains("agent t", "agent d", "best-effort", "may declare the workflow complete")
                .contains("resolvable workflow escalation");
    }

    @Test
    @DisplayName("REQ-AGENT-012/017 - quiet or unreliable continuation -> native-state checks and role-specific recovery")
    void continuationRecoveryShouldUseNativeEvidenceAndRoleSpecificHandling() throws Exception {
        String orchestration = normalized(ORCHESTRATION);

        assertThat(orchestration)
                .contains("elapsed time", "lack of streamed output", "alone")
                .contains("native command", "tool call", "maven", "progress checkpoint")
                .contains("no live work", "repeated progress")
                .contains("agent t", "agent d", "one recovery", "same", "preserved assignment")
                .contains("agent r", "interrupt", "non-resumable", "fresh independent")
                .contains("closure", "best-effort", "not", "precondition");
    }

    @Test
    @DisplayName("REQ-AGENT-018 - fixed capacity 20 -> retained threads count and only actual spawn exhaustion blocks")
    void fixedCapacityShouldBeReconciledBeforeEverySpawn() throws Exception {
        String orchestration = normalized(ORCHESTRATION);

        assertThat(orchestration)
                .contains("before spawning", "reconcile", "native thread state")
                .contains("retained completed", "capacity accounting")
                .contains("actually exhausted", "spawn operation fails")
                .contains("capacity-occupying thread identities", "observed states", "required transition")
                .contains("do not dynamically change", "configured limit")
                .contains("missing close capability", "alone", "shall not")
                .contains("do not reuse", "completed", "agent r");

    }

    @Test
    @DisplayName("REQ-AGENT-018 - supported local configurations -> fixed spawned-thread limit of 20")
    void supportedLocalConfigurationsShouldUseFixedCapacityTwenty() throws Exception {
        for (Path config : List.of(TRACKED_CONFIG, ACTIVE_CONFIG)) {
            assertThat(normalized(config))
                    .as("supported local configuration %s", config)
                    .contains("max_concurrent_threads_per_session = 20");
        }
    }

    @Test
    @DisplayName("REQ-AGENT-004 - sandbox-bound action -> native permission policy remains authoritative")
    void workflowAuthorityShouldRemainSeparateFromPlatformApproval() throws Exception {
        String orchestration = normalized(ORCHESTRATION);

        assertThat(orchestration)
                .contains("auto-review", "native permission policy")
                .contains("sandbox", "filesystem", "network", "tool")
                .contains("agent o", "shall not approve", "permission blocker");
    }

    private static String normalized(Path path) throws IOException {
        return Files.readString(path)
                .replace("\r\n", "\n")
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }

    private static Map<AgentRoleResultContractStructuralTest.RolePhase, Set<String>>
            roleResultGuideOutcomeProjections() throws IOException {
        Map<AgentRoleResultContractStructuralTest.RolePhase, Set<String>> projections =
                new LinkedHashMap<>();

        for (String line : Files.readAllLines(RESULT_GUIDE)) {
            String[] cells = line.split("\\|", -1);
            if (cells.length < 6 || !cells[1].trim().startsWith("Agent ")) {
                continue;
            }

            String roleCell = cells[1].trim();
            String phaseCell = cells[2].trim();
            String outcome = cells[3].trim().replace("`", "");
            for (String role : expandedRoles(roleCell)) {
                for (String phase : expandedPhases(role, phaseCell)) {
                    projections.computeIfAbsent(
                            new AgentRoleResultContractStructuralTest.RolePhase(role, phase),
                            ignored -> new TreeSet<>()
                    ).add(outcome);
                }
            }
        }

        return projections;
    }

    private static List<String> expandedRoles(String roleCell) {
        return switch (roleCell) {
            case "Agent T" -> List.of("agent_t");
            case "Agent D" -> List.of("agent_d");
            case "Agent R" -> List.of("agent_r");
            case "Agent T, D, or R" -> List.of("agent_t", "agent_d", "agent_r");
            default -> throw new AssertionError("Unsupported outcome-table role projection: " + roleCell);
        };
    }

    private static List<String> expandedPhases(String role, String phaseCell) {
        if (phaseCell.equals("any active phase")) {
            return switch (role) {
                case "agent_t" -> List.of("t_initial", "t_expanded");
                case "agent_d" -> List.of("d_initial", "d_correction");
                case "agent_r" -> List.of("r_review");
                default -> throw new AssertionError("Unsupported role: " + role);
            };
        }
        return List.of(phaseCell.split(" or "));
    }
}
