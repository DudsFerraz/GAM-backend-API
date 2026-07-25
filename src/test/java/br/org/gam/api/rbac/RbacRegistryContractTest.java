package br.org.gam.api.rbac;

import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.rbac.role.domain.SystemRole;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@FunctionalTest
@DisplayName("Unit - RBAC Registry Contract")
class RbacRegistryContractTest {

    private static final Map<String, PermissionMetadata> ORATORIO_PERMISSION_METADATA = Map.ofEntries(
            Map.entry("ORATORIO_GET", new PermissionMetadata(
                    "View Oratorios",
                    "Allows viewing specialized Oratorio details"
            )),
            Map.entry("ORATORIO_CREATE", new PermissionMetadata(
                    "Create Oratorios",
                    "Allows creating Oratorio occurrences"
            )),
            Map.entry("ORATORIO_MANAGE", new PermissionMetadata(
                    "Manage Oratorios",
                    "Allows managing Oratorio planning and lifecycle"
            )),
            Map.entry("ORATORIO_ATTENDANCE_GET", new PermissionMetadata(
                    "View Oratorio attendance",
                    "Allows viewing combined Member and Oratoriano attendance trackers"
            )),
            Map.entry("ORATORIO_ATTENDANCE_MANAGE", new PermissionMetadata(
                    "Manage Oratorio attendance",
                    "Allows recording and correcting Member and Oratoriano attendance"
            )),
            Map.entry("ORATORIO_COORD_MANAGE", new PermissionMetadata(
                    "Manage Oratorio coordinators",
                    "Allows granting and revoking Oratorio Coordinator designation"
            )),
            Map.entry("ORATORIANO_GET", new PermissionMetadata(
                    "View Oratorianos",
                    "Allows searching and viewing ordinary Oratoriano profiles"
            )),
            Map.entry("ORATORIANO_REGISTER", new PermissionMetadata(
                    "Register Oratorianos",
                    "Allows registering Oratorianos"
            )),
            Map.entry("ORATORIANO_MANAGE", new PermissionMetadata(
                    "Manage Oratorianos",
                    "Allows correcting, deleting, and restoring Oratoriano records"
            )),
            Map.entry("ORATORIANO_FORM_GET", new PermissionMetadata(
                    "View Oratoriano forms",
                    "Allows viewing sensitive Oratoriano form details"
            )),
            Map.entry("ORATORIANO_FORM_MANAGE", new PermissionMetadata(
                    "Manage Oratoriano forms",
                    "Allows creating and managing Oratoriano form versions"
            )),
            Map.entry("ORATORIANO_FORM_PDF_GENERATE", new PermissionMetadata(
                    "Generate Oratoriano form PDFs",
                    "Allows creating and rendering identified Oratoriano print snapshots"
            )),
            Map.entry("ORATORIANO_FORM_ATTACHMENT_GET", new PermissionMetadata(
                    "Download signed Oratoriano forms",
                    "Allows downloading signed Oratoriano form attachments"
            ))
    );

    private static final Set<String> ORATORIO_OPERATIONS = Set.of(
            "ORATORIO_GET",
            "ORATORIO_CREATE",
            "ORATORIO_MANAGE",
            "ORATORIO_ATTENDANCE_GET",
            "ORATORIO_ATTENDANCE_MANAGE",
            "ORATORIANO_GET",
            "ORATORIANO_REGISTER",
            "ORATORIANO_MANAGE",
            "ORATORIANO_FORM_GET",
            "ORATORIANO_FORM_MANAGE",
            "ORATORIANO_FORM_PDF_GENERATE",
            "ORATORIANO_FORM_ATTACHMENT_GET"
    );

    @Test
    @DisplayName("REQ-RBAC-002 - COORDINATOR_MANAGE has the accepted stable code and display metadata")
    void coordinatorManageShouldHaveAcceptedRegistryMetadata() {
        Map<String, PermissionEnum> permissionsByCode = java.util.Arrays.stream(PermissionEnum.values())
                .collect(Collectors.toMap(PermissionEnum::getCode, Function.identity()));

        assertThat(permissionsByCode).containsKey("COORDINATOR_MANAGE");
        assertThat(permissionsByCode.get("COORDINATOR_MANAGE"))
                .extracting(PermissionEnum::getLabel, PermissionEnum::getDescription)
                .containsExactly(
                        "Manage coordinators",
                        "Allows granting and revoking Coordinator designation"
                );
    }

    @Test
    @DisplayName("REQ-RBAC-003 - SUDO and COORD include COORDINATOR_MANAGE while MEMBER and VISITOR exclude it")
    void baselineBundlesShouldPlaceCoordinatorManageOnlyInSudoAndCoord() {
        assertThat(permissionCodes(SystemRole.SUDO)).contains("COORDINATOR_MANAGE");
        assertThat(permissionCodes(SystemRole.COORD)).contains("COORDINATOR_MANAGE");
        assertThat(permissionCodes(SystemRole.MEMBER)).doesNotContain("COORDINATOR_MANAGE");
        assertThat(permissionCodes(SystemRole.VISITOR)).doesNotContain("COORDINATOR_MANAGE");
    }

    @Test
    @DisplayName("REQ-ORATORIO-COORD-007 - Oratorio and Oratoriano permissions have stable codes and metadata")
    void oratorioPermissionsShouldHaveAcceptedRegistryMetadata() {
        Map<String, PermissionEnum> permissionsByCode = java.util.Arrays.stream(PermissionEnum.values())
                .collect(Collectors.toMap(PermissionEnum::getCode, Function.identity()));

        assertThat(permissionsByCode).containsKeys(ORATORIO_PERMISSION_METADATA.keySet().toArray(String[]::new));
        ORATORIO_PERMISSION_METADATA.forEach((code, expected) ->
                assertThat(permissionsByCode.get(code))
                        .as(code)
                        .extracting(PermissionEnum::getLabel, PermissionEnum::getDescription)
                        .containsExactly(expected.label(), expected.description())
        );
    }

    @Test
    @DisplayName("REQ-ORATORIO-COORD-001 and REQ-ORATORIO-COORD-008 - baseline bundles flatten the accepted Oratorio groups")
    void baselineBundlesShouldFlattenAcceptedOratorioGroups() {
        SystemRole oratorioCoordinator = SystemRole.fromCode("ORATORIO_COORD").orElseThrow();

        assertThat(oratorioCoordinator.getDescription())
                .isEqualTo("Oratorio operational responsibility for an active Member");
        assertThat(permissionCodes(oratorioCoordinator))
                .containsAll(ORATORIO_OPERATIONS)
                .doesNotContain("ORATORIO_COORD_MANAGE");
        assertThat(permissionCodes(SystemRole.COORD))
                .containsAll(ORATORIO_OPERATIONS)
                .contains("ORATORIO_COORD_MANAGE");
        assertThat(permissionCodes(SystemRole.MEMBER))
                .contains("ORATORIO_GET")
                .doesNotContainAnyElementsOf(
                        ORATORIO_OPERATIONS.stream()
                                .filter(code -> !"ORATORIO_GET".equals(code))
                                .collect(Collectors.toSet())
                )
                .doesNotContain("ORATORIO_COORD_MANAGE");
        assertThat(permissionCodes(SystemRole.VISITOR))
                .doesNotContainAnyElementsOf(ORATORIO_OPERATIONS)
                .doesNotContain("ORATORIO_COORD_MANAGE");
        assertThat(permissionCodes(SystemRole.SUDO))
                .containsAll(ORATORIO_PERMISSION_METADATA.keySet());
    }

    private static Set<String> permissionCodes(SystemRole role) {
        return role.getPermissions().stream()
                .map(PermissionEnum::getCode)
                .collect(Collectors.toSet());
    }

    private record PermissionMetadata(String label, String description) {
    }
}
