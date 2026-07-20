package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Account Role Management")
class AccountRoleManagementApiIT extends BaseApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> customRoleIds = new ArrayList<>();

    @AfterEach
    void cleanupCustomRoles() {
        for (UUID roleId : customRoleIds) {
            jdbcTemplate.update("DELETE FROM account_roles WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM roles WHERE id = ?", roleId);
        }
        customRoleIds.clear();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-001 and REQ-ACCOUNT-ROLE-006 - unauthenticated list -> HTTP 401")
    void unauthenticatedRoleListShouldReturnUnauthorized() {
        jsonRequest()
                .get("/accounts/{accountId}/roles", UUID.randomUUID())
                .then()
                .statusCode(401)
                .body("status", equalTo(401));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-001 and REQ-ACCOUNT-ROLE-006 - unauthenticated add -> HTTP 401")
    void unauthenticatedRoleAddShouldReturnUnauthorized() {
        jsonRequest()
                .body(reasonPayload(" "))
                .post("/accounts/{accountId}/roles", UUID.randomUUID())
                .then()
                .statusCode(401)
                .body("status", equalTo(401));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-001 and REQ-ACCOUNT-ROLE-006 - unauthenticated drop -> HTTP 401")
    void unauthenticatedRoleDropShouldReturnUnauthorized() {
        jsonRequest()
                .body(reasonPayload("Remove access"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", UUID.randomUUID(), UUID.randomUUID())
                .then()
                .statusCode(401)
                .body("status", equalTo(401));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-001 - ACCOUNT_GET -> lists active roles as a top-level collection")
    void accountGetPermissionShouldListActiveRoles() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Role List Target");
        grantRole(targetId, "COORD");
        UUID customRoleId = createCustomRole();
        String customRoleName = roleName(customRoleId);
        insertAccountRole(targetId, customRoleId);
        long activityCountBeforeRead = accountRoleActivityCount();

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(200)
                .extract();

        List<Map<String, Object>> roles = response.jsonPath().getList("roles");
        assertThat(roles)
                .extracting(role -> role.get("name"))
                .containsExactlyInAnyOrder("COORD", customRoleName);
        assertRoleShape(roles);
        assertThat(accountRoleActivityCount()).isEqualTo(activityCountBeforeRead);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-002 - Account without active assignments -> empty roles list")
    void accountWithoutActiveRolesShouldReturnAnEmptyList() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "No Roles Target");

        authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(200)
                .body("roles", equalTo(List.of()));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-002 - dropped assignment -> excluded from role collection")
    void droppedAssignmentShouldBeExcludedFromRoleCollection() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Dropped Role Target");
        UUID customRoleId = createCustomRole();
        String customRoleName = roleName(customRoleId);
        insertAccountRole(targetId, customRoleId);
        softDeleteAccountRole(targetId, customRoleName);

        authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(200)
                .body("roles", equalTo(List.of()));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-002 - soft-deleted Role -> excluded from role collection")
    void softDeletedRoleShouldBeExcludedFromRoleCollection() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Deleted Role Collection Target");
        UUID deletedRoleId = createCustomRole();
        insertAccountRole(targetId, deletedRoleId);
        softDeleteRole(deletedRoleId);

        authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(200)
                .body("roles", equalTo(List.of()));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-002 and REQ-ACCOUNT-ROLE-006 - missing or deleted Account -> HTTP 404")
    void missingOrDeletedAccountShouldReturnNotFoundForRoleList() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID deletedAccountId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Deleted Role List Target");
        softDeleteAccount(deletedAccountId);

        authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}/roles", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}/roles", deletedAccountId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-001 - self-view without ACCOUNT_GET -> HTTP 403 for role collection")
    void selfViewShouldNotAuthorizeRoleCollection() {
        AuthSession visitor = registerAndLogin("VISITOR");

        authenticatedJsonRequest(visitor)
                .get("/accounts/{accountId}/roles", visitor.accountId())
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-001 - caller without ACCOUNT_ROLE_MANAGE -> HTTP 403 for add")
    void callerWithoutRoleManagePermissionShouldNotAddRole() {
        AuthSession member = registerAndLogin("MEMBER");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Forbidden Add Target");
        UUID roleId = createCustomRole();

        authenticatedJsonRequest(member)
                .body(addPayload(roleId, "Grant coordinator access"))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(activeAssignmentCount(targetId, roleId)).isZero();
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-001 - caller without ACCOUNT_ROLE_MANAGE -> HTTP 403 for drop")
    void callerWithoutRoleManagePermissionShouldNotDropRole() {
        AuthSession member = registerAndLogin("MEMBER");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Forbidden Drop Target");
        UUID roleId = createCustomRole();
        insertAccountRole(targetId, roleId);

        authenticatedJsonRequest(member)
                .body(reasonPayload("Remove coordinator access"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", targetId, roleId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(activeAssignmentCount(targetId, roleId)).isEqualTo(1);
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-016 - authorized custom-Role add -> HTTP 201, nested Location, and one audit row")
    void authorizedCallerShouldAddRoleAndRecordOneAuditEvent() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Add Role Target");
        UUID roleId = createCustomRole();
        String roleName = roleName(roleId);
        String reason = "Grant custom operational access";

        ExtractableResponse<Response> httpResponse = withUntrustedForwardingHeaders(authenticatedJsonRequest(coordinator))
                .header("X-Request-Id", "account-role-add-request")
                .header("User-Agent", "account-role-test")
                .body(addPayload(roleId, " " + reason + " "))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(201)
                .extract();
        Map<String, Object> response = httpResponse.jsonPath().getMap("$");

        UUID assignmentId = activeAccountRoleId(targetId, roleId);
        assertPublicApiLocation(
                httpResponse,
                "/accounts/" + targetId + "/role-assignments/" + assignmentId
        );
        assertAccountRoleResponse(response, assignmentId, targetId, roleId, roleName);
        assertThat(httpResponse.header("Location"))
                .endsWith("/api/accounts/" + targetId + "/role-assignments/" + assignmentId);
        assertThat(accountRoleActivityCount(assignmentId, "ACCOUNT_ROLE_ADDED")).isEqualTo(1);

        Map<String, Object> activity = accountRoleActivity(assignmentId, "ACCOUNT_ROLE_ADDED");
        assertThat(activity.get("actor_account_id").toString()).isEqualTo(coordinator.accountId().toString());
        assertThat(activity.get("reason")).isEqualTo(reason);
        assertThat(activity.get("request_id")).isEqualTo("account-role-add-request");
        assertThat(activity.get("user_agent")).isEqualTo("account-role-test");
        assertThat(activity.get("account_id").toString()).isEqualTo(targetId.toString());
        assertThat(activity.get("role_id").toString()).isEqualTo(roleId.toString());
        assertThat(activity.get("role_name")).isEqualTo(roleName);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-005 - exactly 2,000 trimmed characters -> accepted and audited")
    void maximumLengthReasonShouldBeAcceptedAndTrimmed() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Maximum Reason Target");
        UUID roleId = createCustomRole();
        String reason = "a".repeat(2_000);

        authenticatedJsonRequest(coordinator)
                .body(addPayload(roleId, " " + reason + " "))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(201);

        UUID assignmentId = activeAccountRoleId(targetId, roleId);
        assertThat(accountRoleActivity(assignmentId, "ACCOUNT_ROLE_ADDED").get("reason"))
                .isEqualTo(reason);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-003 and REQ-ACCOUNT-ROLE-006 - duplicate active assignment -> HTTP 409 without second row or audit")
    void duplicateActiveAssignmentShouldReturnConflictWithoutMutationOrAudit() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Duplicate Role Target");
        UUID roleId = createCustomRole();
        insertAccountRole(targetId, roleId);

        authenticatedJsonRequest(coordinator)
                .body(addPayload(roleId, "Grant duplicate access"))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(409)
                .body("status", equalTo(409));

        assertThat(activeAssignmentCount(targetId, roleId)).isEqualTo(1);
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-003 and REQ-ACCOUNT-ROLE-006 - missing Account or Role during add -> HTTP 404")
    void missingAccountOrRoleShouldReturnNotFoundDuringAdd() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Missing Role Target");
        UUID roleId = createCustomRole();

        authenticatedJsonRequest(coordinator)
                .body(addPayload(roleId, "Grant access"))
                .post("/accounts/{accountId}/roles", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        UUID missingRoleId = UUID.randomUUID();
        authenticatedJsonRequest(coordinator)
                .body(addPayload(missingRoleId, "Grant access"))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-003 - soft-deleted Role during add -> HTTP 404")
    void softDeletedRoleShouldNotBeAddable() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Deleted Role Target");
        UUID deletedRoleId = createCustomRole();
        softDeleteRole(deletedRoleId);

        authenticatedJsonRequest(coordinator)
                .body(addPayload(deletedRoleId, "Grant deleted role access"))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        assertThat(activeAssignmentCount(targetId, deletedRoleId)).isZero();
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-003 and REQ-ACCOUNT-ROLE-006 - missing roleId -> HTTP 400 without mutation")
    void missingRoleIdShouldReturnBadRequestDuringAdd() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Missing Role Id Target");

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Grant access"))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(400)
                .body("status", equalTo(400));

        assertThat(accountRoleActivityCount()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidReasons")
    @DisplayName("REQ-ACCOUNT-ROLE-005 and REQ-ACCOUNT-ROLE-006 - invalid add reason -> HTTP 400 without mutation or audit")
    void invalidAddReasonShouldReturnBadRequest(String reason) {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Invalid Reason Target");
        UUID roleId = roleId("COORD");

        authenticatedJsonRequest(coordinator)
                .body(addPayload(roleId, reason))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(400)
                .body("status", equalTo(400));

        assertThat(activeAssignmentCount(targetId, roleId)).isZero();
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-017 - authorized custom-Role drop -> HTTP 204, soft-delete, and one audit row")
    void authorizedCallerShouldDropRoleAndRecordOneAuditEvent() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Drop Role Target");
        UUID roleId = createCustomRole();
        insertAccountRole(targetId, roleId);
        UUID assignmentId = activeAccountRoleId(targetId, roleId);

        authenticatedJsonRequest(coordinator)
                .header("X-Request-Id", "account-role-drop-request")
                .header("User-Agent", "account-role-test")
                .body(reasonPayload(" Remove custom operational access "))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", targetId, roleId)
                .then()
                .statusCode(204)
                .body(equalTo(""));

        assertThat(activeAssignmentCount(targetId, roleId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM account_roles WHERE id = ?",
                Boolean.class,
                assignmentId
        )).isTrue();
        assertThat(accountRoleActivityCount(assignmentId, "ACCOUNT_ROLE_REMOVED")).isEqualTo(1);
        assertThat(accountRoleActivity(assignmentId, "ACCOUNT_ROLE_REMOVED").get("reason"))
                .isEqualTo("Remove custom operational access");

        authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(200)
                .body("roles", equalTo(List.of()));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-004 and REQ-ACCOUNT-ROLE-006 - missing active assignment during drop -> HTTP 404")
    void missingActiveAssignmentShouldReturnNotFoundDuringDrop() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Missing Assignment Target");
        UUID roleId = createCustomRole();

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Remove missing access"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", targetId, roleId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-004 - soft-deleted assignment during drop -> HTTP 404")
    void softDeletedAssignmentShouldNotBeDroppable() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Deleted Assignment Target");
        UUID roleId = createCustomRole();
        insertAccountRole(targetId, roleId);
        softDeleteAccountRole(targetId, roleName(roleId));

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Remove deleted access"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", targetId, roleId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        assertThat(activeAssignmentCount(targetId, roleId)).isZero();
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-004 - missing Account or soft-deleted Role during drop -> HTTP 404 without mutation")
    void missingAccountOrDeletedRoleShouldReturnNotFoundDuringDrop() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID roleId = createCustomRole();

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Remove missing account access"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", UUID.randomUUID(), roleId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Deleted Role Drop Target");
        UUID deletedRoleId = createCustomRole();
        insertAccountRole(targetId, deletedRoleId);
        softDeleteRole(deletedRoleId);

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Remove deleted role access"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", targetId, deletedRoleId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NULL FROM account_roles WHERE account_id = ? AND role_id = ?",
                Boolean.class,
                targetId,
                deletedRoleId
        )).isTrue();
        assertThat(accountRoleActivityCount()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidReasons")
    @DisplayName("REQ-ACCOUNT-ROLE-005 and REQ-ACCOUNT-ROLE-006 - invalid drop reason -> HTTP 400 without mutation or audit")
    void invalidDropReasonShouldReturnBadRequest(String reason) {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Invalid Drop Reason Target");
        grantRole(targetId, "COORD");
        UUID roleId = roleId("COORD");

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload(reason))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", targetId, roleId)
                .then()
                .statusCode(400)
                .body("status", equalTo(400));

        assertThat(activeAssignmentCount(targetId, roleId)).isEqualTo(1);
        assertThat(accountRoleActivityCount()).isZero();
    }

    @ParameterizedTest
    @MethodSource("lifecycleRoleNames")
    @DisplayName("REQ-ACCOUNT-ROLE-016 and REQ-ACCOUNT-ROLE-018 - HTTP cannot add system-managed Roles")
    void httpCallerShouldNotAddLifecycleOwnedRole(String roleName) {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Lifecycle Role Target");
        UUID roleId = roleId(roleName);

        authenticatedJsonRequest(coordinator)
                .body(addPayload(roleId, "Attempt direct lifecycle-role assignment"))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(activeAssignmentCount(targetId, roleId)).isZero();
        assertThat(accountRoleActivityCount()).isZero();
    }

    @ParameterizedTest
    @MethodSource("lifecycleRoleNames")
    @DisplayName("REQ-ACCOUNT-ROLE-017 and REQ-ACCOUNT-ROLE-018 - HTTP cannot drop system-managed Roles")
    void httpCallerShouldNotDropLifecycleOwnedRole(String roleName) {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Lifecycle Role Target");
        UUID roleId = roleId(roleName);

        grantRole(targetId, roleName);

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Attempt direct lifecycle-role removal"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", targetId, roleId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(activeAssignmentCount(targetId, roleId)).isEqualTo(1);
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-016 through REQ-ACCOUNT-ROLE-018 - future system-managed Role is rejected without name-specific rules")
    void futureSystemManagedRoleShouldBeRejectedForAddAndDrop() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Future System Role Target");
        UUID roleId = createRole(true);

        authenticatedJsonRequest(coordinator)
                .body(addPayload(roleId, "Attempt future system assignment"))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        insertAccountRole(targetId, roleId);
        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Attempt future system removal"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", targetId, roleId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(activeAssignmentCount(targetId, roleId)).isEqualTo(1);
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-018 - system Role eligibility fails before duplicate or missing-assignment evaluation")
    void systemRoleRejectionShouldPrecedeAssignmentStateChecks() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID duplicateTargetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "System Duplicate Target");
        UUID missingTargetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "System Missing Target");
        UUID coordRoleId = roleId("COORD");
        grantRole(duplicateTargetId, "COORD");

        authenticatedJsonRequest(coordinator)
                .body(addPayload(coordRoleId, "Duplicate system assignment"))
                .post("/accounts/{accountId}/roles", duplicateTargetId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Missing system assignment"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", missingTargetId, coordRoleId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(activeAssignmentCount(duplicateTargetId, coordRoleId)).isEqualTo(1);
        assertThat(activeAssignmentCount(missingTargetId, coordRoleId)).isZero();
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-003 and REQ-ACCOUNT-ROLE-004 - HTTP cannot add or drop SUDO")
    void httpCallerShouldNotManageSudo() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Sudo Target");
        UUID sudoRoleId = roleId("SUDO");

        authenticatedJsonRequest(coordinator)
                .body(addPayload(sudoRoleId, "Grant developer recovery access"))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(activeAssignmentCount(targetId, sudoRoleId)).isZero();

        grantRole(targetId, "SUDO");

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Remove developer recovery access"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", targetId, sudoRoleId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(activeAssignmentCount(targetId, sudoRoleId)).isEqualTo(1);
        assertThat(accountRoleActivityCount()).isZero();
    }

    private static Stream<Arguments> invalidReasons() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of(" \n\t"),
                Arguments.of("a".repeat(2_001))
        );
    }

    private static Stream<String> lifecycleRoleNames() {
        return Stream.of("MEMBER", "VISITOR", "COORD");
    }

    private Map<String, Object> addPayload(UUID roleId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("roleId", roleId.toString());
        payload.put("reason", reason);
        return payload;
    }

    private static Map<String, Object> reasonPayload(String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        return payload;
    }

    private UUID roleId(String roleName) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = ? AND deleted_at IS NULL",
                UUID.class,
                roleName
        );
    }

    private String roleName(UUID roleId) {
        return jdbcTemplate.queryForObject(
                "SELECT name FROM roles WHERE id = ? AND deleted_at IS NULL",
                String.class,
                roleId
        );
    }

    private UUID activeAccountRoleId(UUID accountId, UUID roleId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM account_roles WHERE account_id = ? AND role_id = ? AND deleted_at IS NULL",
                UUID.class,
                accountId,
                roleId
        );
    }

    private long activeAssignmentCount(UUID accountId, UUID roleId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_roles WHERE account_id = ? AND role_id = ? AND deleted_at IS NULL",
                Long.class,
                accountId,
                roleId
        );
    }

    private long accountRoleActivityCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE target_type = 'ACCOUNT_ROLE'",
                Long.class
        );
    }

    private long accountRoleActivityCount(UUID assignmentId, String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs "
                        + "WHERE target_type = 'ACCOUNT_ROLE' AND action = ? AND target_id = ?",
                Long.class,
                action,
                assignmentId
        );
    }

    private Map<String, Object> accountRoleActivity(UUID assignmentId, String action) {
        return jdbcTemplate.queryForMap(
                "SELECT actor_account_id, reason, request_id, user_agent, "
                        + "metadata ->> 'accountId' AS account_id, "
                        + "metadata ->> 'roleId' AS role_id, "
                        + "metadata ->> 'roleName' AS role_name "
                        + "FROM activity_logs "
                        + "WHERE target_type = 'ACCOUNT_ROLE' AND action = ? AND target_id = ?",
                action,
                assignmentId
        );
    }

    private UUID createCustomRole() {
        return createRole(false);
    }

    private UUID createRole(boolean systemManaged) {
        UUID roleId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO roles (id, name, description, system_managed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                roleId,
                "API_CUSTOM_" + roleId,
                "API custom role",
                systemManaged,
                now,
                now
        );
        customRoleIds.add(roleId);
        return roleId;
    }

    private void softDeleteRole(UUID roleId) {
        jdbcTemplate.update("UPDATE roles SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", roleId);
    }

    private void insertAccountRole(UUID accountId, UUID roleId) {
        jdbcTemplate.update(
                "INSERT INTO account_roles (id, account_id, role_id, created_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                accountId,
                roleId,
                Timestamp.from(Instant.now())
        );
    }

    private static void assertRoleShape(List<Map<String, Object>> roles) {
        for (Map<String, Object> role : roles) {
            assertThat(role)
                    .containsKeys("id", "name", "description", "systemManaged")
                    .doesNotContainKeys("deletedAt", "deletedBy", "createdAt", "createdBy", "updatedAt", "updatedBy");
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertAccountRoleResponse(
            Map<String, Object> response,
            UUID assignmentId,
            UUID accountId,
            UUID roleId,
            String roleName
    ) {
        assertThat(response).containsOnlyKeys("id", "account", "role");
        assertThat(response.get("id")).isEqualTo(assignmentId.toString());

        Map<String, Object> account = (Map<String, Object>) response.get("account");
        assertThat(account)
                .containsKeys("id", "email", "displayName", "roles")
                .doesNotContainKeys(
                        "password", "passwordHash", "accessToken", "refreshToken", "sessions",
                        "deletedAt", "deletedBy", "createdAt", "createdBy", "updatedAt", "updatedBy"
                );
        assertThat(account.get("id")).isEqualTo(accountId.toString());
        assertThat(account.get("roles")).isInstanceOf(List.class);
        List<Map<String, Object>> accountRoles = (List<Map<String, Object>>) account.get("roles");
        assertThat(accountRoles)
                .singleElement()
                .satisfies(accountRole -> {
                    assertThat(accountRole)
                            .containsEntry("id", roleId.toString())
                            .containsEntry("name", roleName);
                    assertRoleShape(List.of(accountRole));
                });

        Map<String, Object> role = (Map<String, Object>) response.get("role");
        assertThat(role)
                .containsKeys("id", "name", "description", "systemManaged")
                .doesNotContainKeys("deletedAt", "deletedBy", "createdAt", "createdBy", "updatedAt", "updatedBy");
        assertThat(role.get("id")).isEqualTo(roleId.toString());
        assertThat(role.get("name")).isEqualTo(roleName);
    }

    private String uniqueEmail() {
        return "account-role-" + UUID.randomUUID() + "@example.com";
    }
}
