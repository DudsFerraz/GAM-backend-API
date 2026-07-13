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
        grantRole(targetId, "MEMBER");
        grantRole(targetId, "COORD");
        long activityCountBeforeRead = accountRoleActivityCount();

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(200)
                .extract();

        List<Map<String, Object>> roles = response.jsonPath().getList("roles");
        assertThat(roles)
                .extracting(role -> role.get("name"))
                .containsExactlyInAnyOrder("MEMBER", "COORD");
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
        grantRole(targetId, "MEMBER");
        softDeleteAccountRole(targetId, "MEMBER");

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
        UUID roleId = roleId("MEMBER");

        authenticatedJsonRequest(member)
                .body(addPayload(roleId, "Grant member access"))
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
        grantRole(targetId, "MEMBER");
        UUID roleId = roleId("MEMBER");

        authenticatedJsonRequest(member)
                .body(reasonPayload("Remove member access"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", targetId, roleId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(activeAssignmentCount(targetId, roleId)).isEqualTo(1);
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-003 and REQ-ACCOUNT-ROLE-007 - authorized add -> HTTP 201, nested Location, and one audit row")
    void authorizedCallerShouldAddRoleAndRecordOneAuditEvent() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Add Role Target");
        UUID roleId = roleId("MEMBER");
        String reason = "Grant member access";

        ExtractableResponse<Response> httpResponse = authenticatedJsonRequest(coordinator)
                .header("X-Request-Id", "account-role-add-request")
                .header("User-Agent", "account-role-test")
                .body(addPayload(roleId, " " + reason + " "))
                .post("/accounts/{accountId}/roles", targetId)
                .then()
                .statusCode(201)
                .header("Location", containsString("/accounts/" + targetId + "/roles/"))
                .extract();
        Map<String, Object> response = httpResponse.jsonPath().getMap("$");

        assertAccountRoleResponse(response, targetId, roleId, "MEMBER");
        UUID assignmentId = activeAccountRoleId(targetId, roleId);
        assertThat(httpResponse.header("Location"))
                .endsWith("/accounts/" + targetId + "/roles/" + assignmentId);
        assertThat(accountRoleActivityCount(assignmentId, "ACCOUNT_ROLE_ADDED")).isEqualTo(1);

        Map<String, Object> activity = accountRoleActivity(assignmentId, "ACCOUNT_ROLE_ADDED");
        assertThat(activity.get("actor_account_id").toString()).isEqualTo(coordinator.accountId().toString());
        assertThat(activity.get("reason")).isEqualTo(reason);
        assertThat(activity.get("request_id")).isEqualTo("account-role-add-request");
        assertThat(activity.get("user_agent")).isEqualTo("account-role-test");
        assertThat(activity.get("account_id").toString()).isEqualTo(targetId.toString());
        assertThat(activity.get("role_id").toString()).isEqualTo(roleId.toString());
        assertThat(activity.get("role_name")).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-005 - exactly 2,000 trimmed characters -> accepted and audited")
    void maximumLengthReasonShouldBeAcceptedAndTrimmed() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Maximum Reason Target");
        UUID roleId = roleId("MEMBER");
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
        grantRole(targetId, "MEMBER");
        UUID roleId = roleId("MEMBER");

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
        UUID roleId = roleId("MEMBER");

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
        UUID roleId = roleId("MEMBER");

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
    @DisplayName("REQ-ACCOUNT-ROLE-004 and REQ-ACCOUNT-ROLE-007 - authorized drop -> HTTP 204, soft-delete, and one audit row")
    void authorizedCallerShouldDropRoleAndRecordOneAuditEvent() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Drop Role Target");
        grantRole(targetId, "MEMBER");
        UUID roleId = roleId("MEMBER");
        UUID assignmentId = activeAccountRoleId(targetId, roleId);

        authenticatedJsonRequest(coordinator)
                .header("X-Request-Id", "account-role-drop-request")
                .header("User-Agent", "account-role-test")
                .body(reasonPayload(" Remove member access "))
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
                .isEqualTo("Remove member access");

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
        UUID roleId = roleId("MEMBER");

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
        grantRole(targetId, "MEMBER");
        UUID roleId = roleId("MEMBER");
        softDeleteAccountRole(targetId, "MEMBER");

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
        UUID roleId = roleId("MEMBER");

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
        grantRole(targetId, "MEMBER");
        UUID roleId = roleId("MEMBER");

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload(reason))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", targetId, roleId)
                .then()
                .statusCode(400)
                .body("status", equalTo(400));

        assertThat(activeAssignmentCount(targetId, roleId)).isEqualTo(1);
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

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-008 - Coordinator cannot drop their only active COORD capability")
    void coordinatorCannotDropOnlyOwnCoordRole() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID coordRoleId = roleId("COORD");

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Remove coordinator access"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", coordinator.accountId(), coordRoleId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));

        assertThat(activeAssignmentCount(coordinator.accountId(), coordRoleId)).isEqualTo(1);
        assertThat(accountRoleActivityCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-008 - Coordinator may drop own COORD role when another Coordinator remains")
    void coordinatorMayDropOwnCoordRoleWhenAnotherCoordinatorRemains() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID otherCoordinatorId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Other Coordinator");
        grantRole(otherCoordinatorId, "COORD");
        UUID coordRoleId = roleId("COORD");

        authenticatedJsonRequest(coordinator)
                .body(reasonPayload("Remove coordinator access"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", coordinator.accountId(), coordRoleId)
                .then()
                .statusCode(204);

        assertThat(activeAssignmentCount(coordinator.accountId(), coordRoleId)).isZero();
        assertThat(activeAssignmentCount(otherCoordinatorId, coordRoleId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-ROLE-008 - SUDO may drop the final COORD role from self")
    void sudoMayDropFinalOwnCoordRole() {
        AuthSession sudo = registerAndLogin("SUDO");
        grantRole(sudo.accountId(), "COORD");
        UUID coordRoleId = roleId("COORD");

        authenticatedJsonRequest(sudo)
                .body(reasonPayload("Remove coordinator access through SUDO maintenance"))
                .patch("/accounts/{accountId}/roles/{roleId}/drop", sudo.accountId(), coordRoleId)
                .then()
                .statusCode(204);

        assertThat(activeAssignmentCount(sudo.accountId(), coordRoleId)).isZero();
    }

    private static Stream<Arguments> invalidReasons() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of(" \n\t"),
                Arguments.of("a".repeat(2_001))
        );
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
        UUID roleId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO roles (id, name, description, system_managed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, FALSE, ?, ?)",
                roleId,
                "API_CUSTOM_" + roleId,
                "API custom role",
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
            UUID accountId,
            UUID roleId,
            String roleName
    ) {
        assertThat(response)
                .containsKeys("account", "role")
                .doesNotContainKeys("assignmentId", "reason");

        Map<String, Object> account = (Map<String, Object>) response.get("account");
        assertThat(account)
                .containsKeys("id", "email", "displayName", "roles")
                .doesNotContainKeys(
                        "password", "passwordHash", "accessToken", "refreshToken", "sessions",
                        "deletedAt", "deletedBy", "createdAt", "createdBy", "updatedAt", "updatedBy"
                );
        assertThat(account.get("id")).isEqualTo(accountId.toString());
        assertThat(account.get("roles")).isInstanceOf(List.class);
        assertThat((List<?>) account.get("roles")).isEmpty();

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
