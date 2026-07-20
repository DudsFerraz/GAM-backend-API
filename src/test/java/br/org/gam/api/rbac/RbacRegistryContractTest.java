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

    private static Set<String> permissionCodes(SystemRole role) {
        return role.getPermissions().stream()
                .map(PermissionEnum::getCode)
                .collect(Collectors.toSet());
    }
}
