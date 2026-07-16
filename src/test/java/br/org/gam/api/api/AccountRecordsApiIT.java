package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Account Records")
class AccountRecordsApiIT extends BaseApiIntegrationTest {

    private final List<UUID> currentContextRoleIds = new ArrayList<>();
    private final List<UUID> currentContextPermissionIds = new ArrayList<>();
    private final List<UUID> currentContextRolePermissionIds = new ArrayList<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanupCurrentContextRbacFixtures() {
        for (UUID rolePermissionId : currentContextRolePermissionIds) {
            jdbcTemplate.update("DELETE FROM role_permissions WHERE id = ?", rolePermissionId);
        }
        for (UUID roleId : currentContextRoleIds) {
            jdbcTemplate.update("DELETE FROM account_roles WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM roles WHERE id = ?", roleId);
        }
        for (UUID permissionId : currentContextPermissionIds) {
            jdbcTemplate.update("DELETE FROM permissions WHERE id = ?", permissionId);
        }
        currentContextRolePermissionIds.clear();
        currentContextRoleIds.clear();
        currentContextPermissionIds.clear();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-001 - unauthenticated lookup -> HTTP 401")
    void unauthenticatedAccountLookupShouldReturnUnauthorized() {
        jsonRequest()
                .get("/accounts/{accountId}", UUID.randomUUID())
                .then()
                .statusCode(401)
                .body("status", equalTo(401));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-001 - ACCOUNT_GET -> retrieves another account record")
    void accountGetPermissionShouldRetrieveAnotherAccountRecord() {
        AuthSession coordinator = registerAndLogin("COORD");
        String targetEmail = uniqueEmail();
        UUID targetId = registerAccount(targetEmail, TEST_PASSWORD, "Account Record Target");

        authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}", targetId)
                .then()
                .statusCode(200)
                .body("id", equalTo(targetId.toString()))
                .body("email", equalTo(targetEmail));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-001 - self-view without ACCOUNT_GET -> retrieves own account record")
    void selfViewWithoutAccountGetPermissionShouldRetrieveOwnAccountRecord() {
        AuthSession visitor = registerAndLogin("VISITOR");

        authenticatedJsonRequest(visitor)
                .get("/accounts/{accountId}", visitor.accountId())
                .then()
                .statusCode(200)
                .body("id", equalTo(visitor.accountId().toString()));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-002 - caller without ACCOUNT_GET viewing another account -> HTTP 403")
    void callerWithoutAccountGetPermissionShouldNotRetrieveAnotherAccountRecord() {
        AuthSession visitor = registerAndLogin("VISITOR");
        UUID otherAccountId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Other Account");

        authenticatedJsonRequest(visitor)
                .get("/accounts/{accountId}", otherAccountId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-002 - nonexistent account lookup -> HTTP 404")
    void nonexistentAccountLookupShouldReturnNotFound() {
        AuthSession coordinator = registerAndLogin("COORD");

        authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-002 - soft-deleted account lookup -> HTTP 404")
    void softDeletedAccountLookupShouldReturnNotFound() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID deletedAccountId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Deleted Account");
        softDeleteAccount(deletedAccountId);

        authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}", deletedAccountId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-003 - self-view does not authorize account search -> HTTP 403")
    void selfViewShouldNotAuthorizeAccountSearch() {
        AuthSession visitor = registerAndLogin("VISITOR");

        authenticatedJsonRequest(visitor)
                .body(searchPayload(filter("id", visitor.accountId().toString(), "EQUALS")))
                .post("/accounts/search")
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-004 - empty filters -> paginated visible active accounts only")
    void emptyFiltersShouldBrowseVisibleActiveAccountsInAPage() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID activeAccountId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Active Browse Account");
        UUID deletedAccountId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Deleted Browse Account");
        softDeleteAccount(deletedAccountId);

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(searchPayload())
                .post("/accounts/search?size=100")
                .then()
                .statusCode(200)
                .extract();

        Map<String, Object> page = response.jsonPath().getMap("$");
        assertThat(page).containsKeys("items", "totalElements");
        assertThat(accountIds(response.jsonPath().getList("items")))
                .contains(activeAccountId)
                .doesNotContain(deletedAccountId);
    }

    @Test
    @DisplayName("REQ-ACCOUNT-005 - documented public filters and methods -> match the account")
    void documentedPublicFiltersAndMethodsShouldMatchTheAccount() {
        AuthSession coordinator = registerAndLogin("COORD");
        String targetEmail = uniqueEmail();
        String targetDisplayName = "Account Record Search Target";
        UUID targetId = registerAccount(targetEmail, TEST_PASSWORD, targetDisplayName);
        grantRole(targetId, "MEMBER");

        List<Map<String, Object>> filters = List.of(
                filter("id", targetId.toString(), "EQUALS"),
                filter("id", List.of(targetId.toString()), "IN"),
                filter("email", targetEmail, "EQUALS"),
                filter("email", targetEmail.substring(0, targetEmail.indexOf('@')), "LIKE"),
                filter("displayName", targetDisplayName, "EQUALS"),
                filter("displayName", "Search Target", "LIKE"),
                filter("role", "MEMBER", "EQUALS"),
                filter("role", List.of("MEMBER", "COORD"), "IN"),
                filter("createdAt", "2000-01-01T00:00:00Z", "GREATER_THAN_OR_EQUAL"),
                filter("createdAt", "2999-01-01T00:00:00Z", "LESS_THAN_OR_EQUAL"),
                filter("updatedAt", "2000-01-01T00:00:00Z", "GREATER_THAN_OR_EQUAL"),
                filter("updatedAt", "2999-01-01T00:00:00Z", "LESS_THAN_OR_EQUAL")
        );

        for (Map<String, Object> filter : filters) {
            ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                    .body(searchPayload(filter))
                    .post("/accounts/search?size=100")
                    .then()
                    .extract();

            assertThat(response.statusCode())
                    .as("public filter %s %s", filter.get("field"), filter.get("comparationMethod"))
                    .isEqualTo(200);
            assertThat(accountIds(response.jsonPath().getList("items")))
                    .as("public filter %s %s", filter.get("field"), filter.get("comparationMethod"))
                    .contains(targetId);
        }
    }

    @Test
    @DisplayName("REQ-ACCOUNT-005 - unsupported documented-field methods -> HTTP 400 naming the public field")
    void unsupportedComparisonMethodsShouldBeRejectedUsingThePublicFieldName() {
        AuthSession coordinator = registerAndLogin("COORD");

        List<Map<String, Object>> unsupportedFilters = List.of(
                filter("id", UUID.randomUUID().toString(), "LIKE"),
                filter("email", List.of("user@example.com"), "IN"),
                filter("displayName", List.of("User"), "IN"),
                filter("role", "MEMBER", "LIKE"),
                filter("createdAt", "2026-01-01T00:00:00Z", "LIKE"),
                filter("updatedAt", "2026-01-01T00:00:00Z", "EQUALS")
        );

        for (Map<String, Object> filter : unsupportedFilters) {
            authenticatedJsonRequest(coordinator)
                    .body(searchPayload(filter))
                    .post("/accounts/search")
                    .then()
                    .statusCode(400)
                    .body("code", equalTo("INVALID_SEARCH_FILTER"))
                    .body("message", containsString((String) filter.get("field")));
        }
    }

    @Test
    @DisplayName("REQ-ACCOUNT-005 - legacy roleName filter -> generic HTTP 400")
    void legacyRoleNameFilterShouldBeRejected() {
        AuthSession coordinator = registerAndLogin("COORD");

        authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter("roleName", "MEMBER", "EQUALS")))
                .post("/accounts/search")
                .then()
                .statusCode(400)
                .body("message", equalTo("Unknown filter field."));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-005 - internal persistence path filter -> HTTP 400 without path disclosure")
    void internalPersistencePathFilterShouldNotBeAcceptedOrDisclosed() {
        AuthSession coordinator = registerAndLogin("COORD");
        String internalPath = "accountRoles.role.name";

        authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter(internalPath, "MEMBER", "EQUALS")))
                .post("/accounts/search")
                .then()
                .statusCode(400)
                .body("message", not(containsString(internalPath)));
    }

    @Test
    @DisplayName("REQ-ACCOUNT-006 and REQ-ACCOUNT-007 - lookup response -> flat active roles and no sensitive metadata")
    void lookupResponseShouldExposeFlatActiveRolesWithoutSensitiveOrAuditFields() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Response Shape Target");
        grantRole(targetId, "MEMBER");

        Map<String, Object> accountRecord = authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}", targetId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertAccountRecord(accountRecord, targetId, "MEMBER");
    }

    @Test
    @DisplayName("REQ-ACCOUNT-006 and REQ-ACCOUNT-007 - search response -> flat active roles and no sensitive metadata")
    void searchResponseShouldExposeFlatActiveRolesWithoutSensitiveOrAuditFields() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Search Response Shape Target");
        grantRole(targetId, "MEMBER");

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(searchPayload(filter("id", targetId.toString(), "EQUALS")))
                .post("/accounts/search")
                .then()
                .statusCode(200)
                .extract();

        assertAccountRecord(accountRecord(response.jsonPath().getList("items"), targetId), targetId, "MEMBER");
    }

    @Test
    @DisplayName("REQ-ACCOUNT-006 - account without active roles -> empty roles list")
    void accountWithoutActiveRolesShouldReturnAnEmptyRolesList() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "No Roles Target");

        Map<String, Object> accountRecord = authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}", targetId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(accountRecord.get("roles")).isInstanceOf(List.class);
        assertThat((List<?>) accountRecord.get("roles")).isEmpty();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-006 - soft-deleted role assignment -> excluded from roles")
    void softDeletedRoleAssignmentShouldBeExcludedFromRoles() {
        AuthSession coordinator = registerAndLogin("COORD");
        UUID targetId = registerAccount(uniqueEmail(), TEST_PASSWORD, "Deleted Role Assignment Target");
        grantRole(targetId, "MEMBER");
        softDeleteAccountRole(targetId, "MEMBER");

        Map<String, Object> accountRecord = authenticatedJsonRequest(coordinator)
                .get("/accounts/{accountId}", targetId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(accountRecord.get("roles")).isInstanceOf(List.class);
        assertThat((List<?>) accountRecord.get("roles")).isEmpty();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-008 - unauthenticated current-context request -> HTTP 401 without Account data")
    void unauthenticatedCurrentContextShouldReturnUnauthorizedWithoutAccountData() {
        ExtractableResponse<Response> response = jsonRequest()
                .get("/accounts/me")
                .then()
                .statusCode(401)
                .body("status", equalTo(401))
                .extract();

        assertThat(response.jsonPath().getMap("$"))
                .doesNotContainKeys("id", "email", "displayName", "roles", "permissions");
    }

    @Test
    @DisplayName("REQ-ACCOUNT-008 and REQ-BROWSER-AUTH-006 - refresh cookie without bearer token -> HTTP 401")
    void refreshCookieAloneShouldNotAuthenticateCurrentContext() {
        AuthSession visitor = registerAndLogin("VISITOR");

        ExtractableResponse<Response> response = jsonRequest()
                .cookie("refreshToken", visitor.refreshToken())
                .get("/accounts/me")
                .then()
                .statusCode(401)
                .body("status", equalTo(401))
                .extract();

        assertThat(response.jsonPath().getMap("$"))
                .doesNotContainKeys("id", "email", "displayName", "roles", "permissions");
    }

    @Test
    @DisplayName("REQ-ACCOUNT-008 - literal /accounts/me without ACCOUNT_GET -> dedicated current-context route succeeds")
    void currentContextWithoutAccountGetShouldResolveTheDedicatedRoute() {
        AuthSession visitor = registerAndLogin("VISITOR");

        Map<String, Object> currentContext = authenticatedJsonRequest(visitor)
                .get("/accounts/me")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertCurrentContextShape(currentContext, visitor.accountId(), visitor.email(), "API VISITOR");
        assertThat(roleNames(currentContext)).containsExactly("VISITOR");
        assertPermissions(currentContext).isEmpty();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-008 - no active roles or permissions -> empty lists")
    void currentContextWithoutActiveRolesOrPermissionsShouldReturnEmptyLists() {
        String email = uniqueEmail();
        String displayName = "Current Account Without Roles";
        UUID accountId = registerAccount(email, TEST_PASSWORD, displayName);
        ExtractableResponse<Response> loginResponse = login(email, TEST_PASSWORD);
        AuthSession session = new AuthSession(
                accountId,
                email,
                TEST_PASSWORD,
                loginResponse.path("token"),
                loginResponse.cookie("refreshToken")
        );

        Map<String, Object> currentContext = authenticatedJsonRequest(session)
                .get("/accounts/me")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertCurrentContextShape(currentContext, accountId, email, displayName);
        assertThat(roleNames(currentContext)).isEmpty();
        assertPermissions(currentContext).isEmpty();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-008 - permission granted through multiple roles -> one effective permission code")
    void duplicatePermissionGrantsShouldReturnDistinctEffectivePermissionCodes() {
        AuthSession coordinator = registerAndLogin("COORD");
        grantRole(coordinator.accountId(), "MEMBER");

        Map<String, Object> currentContext = authenticatedJsonRequest(coordinator)
                .get("/accounts/me")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(roleNames(currentContext)).containsExactlyInAnyOrder("COORD", "MEMBER");
        assertPermissions(currentContext)
                .contains("ACCOUNT_GET", "EVENT_SEARCH")
                .doesNotContain("COORD", "MEMBER", "ROLE_COORD", "ROLE_MEMBER")
                .doesNotHaveDuplicates();
    }

    @Test
    @PersistenceTest
    @DisplayName("REQ-ACCOUNT-008 and REQ-BROWSER-AUTH-008 - soft-deleted role-permission link -> same token omits permission but retains role")
    void softDeletedRolePermissionShouldBeResynchronizedWithoutRemovingTheRole() {
        AuthSession account = registerAndLogin(null);
        String roleName = "CURRENT_CONTEXT_" + UUID.randomUUID().toString().substring(0, 8);
        UUID roleId = createCurrentContextRole(roleName);
        UUID rolePermissionId = linkCurrentContextPermission(roleId, permissionId("EVENT_SEARCH"));
        grantRole(account.accountId(), roleName);

        Map<String, Object> initialContext = authenticatedJsonRequest(account)
                .get("/accounts/me")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
        assertThat(roleNames(initialContext)).containsExactly(roleName);
        assertPermissions(initialContext).containsExactly("EVENT_SEARCH");

        jdbcTemplate.update(
                "UPDATE role_permissions SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                rolePermissionId
        );

        Map<String, Object> updatedContext = authenticatedJsonRequest(account)
                .get("/accounts/me")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(roleNames(updatedContext)).containsExactly(roleName);
        assertPermissions(updatedContext).isEmpty();
    }

    @Test
    @PersistenceTest
    @DisplayName("REQ-RBAC-005 and REQ-ACCOUNT-008 - stale system Permission on a custom Role -> preserved but grants no authority")
    void staleSystemPermissionShouldNotAppearInCurrentContext() {
        AuthSession account = registerAndLogin(null);
        String roleName = "STALE_PERMISSION_ROLE_" + shortFixtureId();
        UUID roleId = createCurrentContextRole(roleName);
        String stalePermissionCode = "STALE_PERMISSION_" + shortFixtureId();
        UUID stalePermissionId = createCurrentContextPermission(stalePermissionCode, true);
        linkCurrentContextPermission(roleId, stalePermissionId);
        grantRole(account.accountId(), roleName);

        Map<String, Object> currentContext = authenticatedJsonRequest(account)
                .get("/accounts/me")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(roleNames(currentContext)).containsExactly(roleName);
        assertPermissions(currentContext).doesNotContain(stalePermissionCode);
    }

    @Test
    @PersistenceTest
    @DisplayName("REQ-RBAC-005 and REQ-ACCOUNT-008 - stale system Role assignment -> hidden and grants no authority")
    void staleSystemRoleShouldNotAppearInCurrentContextOrGrantPermissions() {
        AuthSession account = registerAndLogin(null);
        String staleRoleName = "STALE_SYSTEM_ROLE_" + shortFixtureId();
        UUID staleRoleId = createCurrentContextRole(staleRoleName, true);
        linkCurrentContextPermission(staleRoleId, permissionId("EVENT_SEARCH"));
        grantRole(account.accountId(), staleRoleName);

        Map<String, Object> currentContext = authenticatedJsonRequest(account)
                .get("/accounts/me")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(roleNames(currentContext)).doesNotContain(staleRoleName);
        assertPermissions(currentContext).doesNotContain("EVENT_SEARCH");
    }

    @Test
    @PersistenceTest
    @DisplayName("REQ-RBAC-003/005 - stale system-role bundle link -> absent from context and cannot authorize backend reads")
    void staleSystemRoleBundleLinkShouldNotGrantBackendAuthority() {
        AuthSession member = registerAndLogin("MEMBER");
        UUID memberRoleId = activeRoleId("MEMBER");
        linkCurrentContextPermission(memberRoleId, permissionId("ROLE_GET"));

        Map<String, Object> currentContext = authenticatedJsonRequest(member)
                .get("/accounts/me")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
        ExtractableResponse<Response> catalogResponse = authenticatedJsonRequest(member)
                .get("/roles/{roleId}", memberRoleId)
                .then()
                .extract();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(permissions(currentContext)).doesNotContain("ROLE_GET");
            softly.assertThat(catalogResponse.statusCode()).isEqualTo(403);
        });
    }

    @Test
    @DisplayName("REQ-ACCOUNT-008 and REQ-BROWSER-AUTH-008 - same valid token after role removal -> current permissions are resynchronized")
    void laterCurrentContextRequestShouldReflectRemovedEffectivePermissionUsingTheSameToken() {
        AuthSession member = registerAndLogin("MEMBER");

        Map<String, Object> initialContext = authenticatedJsonRequest(member)
                .get("/accounts/me")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
        assertPermissions(initialContext).contains("EVENT_SEARCH");

        softDeleteAccountRole(member.accountId(), "MEMBER");

        Map<String, Object> updatedContext = authenticatedJsonRequest(member)
                .get("/accounts/me")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");

        assertThat(roleNames(updatedContext)).isEmpty();
        assertPermissions(updatedContext).doesNotContain("EVENT_SEARCH");
    }

    @Test
    @DisplayName("REQ-ACCOUNT-008 - soft-deleted current Account with a still-issued token -> HTTP 401 without partial context")
    void softDeletedCurrentAccountShouldReturnUnauthorizedWithoutPartialContext() {
        AuthSession visitor = registerAndLogin("VISITOR");
        softDeleteAccount(visitor.accountId());

        ExtractableResponse<Response> response = authenticatedJsonRequest(visitor)
                .get("/accounts/me")
                .then()
                .statusCode(401)
                .body("status", equalTo(401))
                .extract();

        assertThat(response.jsonPath().getMap("$"))
                .doesNotContainKeys("id", "email", "displayName", "roles", "permissions");
    }

    private String uniqueEmail() {
        return "account-records-" + UUID.randomUUID() + "@example.com";
    }

    private static Map<String, Object> searchPayload(Map<String, Object>... filters) {
        return Map.of("filters", List.of(filters));
    }

    private static Map<String, Object> filter(String field, Object value, String comparisonMethod) {
        return Map.of(
                "field", field,
                "value", value,
                "comparationMethod", comparisonMethod
        );
    }

    private static List<UUID> accountIds(List<Map<String, Object>> accountRecords) {
        return accountRecords.stream()
                .map(accountRecord -> UUID.fromString((String) accountRecord.get("id")))
                .toList();
    }

    private static Map<String, Object> accountRecord(List<Map<String, Object>> accountRecords, UUID accountId) {
        return accountRecords.stream()
                .filter(accountRecord -> accountId.toString().equals(accountRecord.get("id")))
                .findFirst()
                .orElseThrow();
    }

    private UUID createCurrentContextRole(String roleName) {
        return createCurrentContextRole(roleName, false);
    }

    private UUID createCurrentContextRole(String roleName, boolean systemManaged) {
        UUID roleId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO roles (id, name, description, system_managed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                roleId,
                roleName,
                "Current Account context persistence fixture",
                systemManaged,
                now,
                now
        );
        currentContextRoleIds.add(roleId);
        return roleId;
    }

    private UUID createCurrentContextPermission(String permissionCode, boolean systemManaged) {
        UUID permissionId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO permissions "
                        + "(id, code, label, description, system_managed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                permissionId,
                permissionCode,
                "Stale permission fixture",
                "Preserved permission absent from the accepted registry",
                systemManaged,
                now,
                now
        );
        currentContextPermissionIds.add(permissionId);
        return permissionId;
    }

    private UUID linkCurrentContextPermission(UUID roleId, UUID permissionId) {
        UUID rolePermissionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO role_permissions (id, role_id, permission_id, created_at) VALUES (?, ?, ?, ?)",
                rolePermissionId,
                roleId,
                permissionId,
                Timestamp.from(Instant.now())
        );
        currentContextRolePermissionIds.add(rolePermissionId);
        return rolePermissionId;
    }

    private UUID activeRoleId(String roleName) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = ? AND deleted_at IS NULL",
                UUID.class,
                roleName
        );
    }

    private String shortFixtureId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static void assertCurrentContextShape(
            Map<String, Object> currentContext,
            UUID accountId,
            String email,
            String displayName
    ) {
        assertThat(currentContext)
                .containsOnlyKeys("id", "email", "displayName", "roles", "permissions")
                .containsEntry("id", accountId.toString())
                .containsEntry("email", email)
                .containsEntry("displayName", displayName);
        assertThat(currentContext.get("roles")).isInstanceOf(List.class);
        assertThat(currentContext.get("permissions")).isInstanceOf(List.class);

        roles(currentContext).forEach(role -> assertThat(role)
                .containsOnlyKeys("id", "name", "description", "systemManaged"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> roles(Map<String, Object> currentContext) {
        return (List<Map<String, Object>>) currentContext.get("roles");
    }

    private static List<String> roleNames(Map<String, Object> currentContext) {
        return roles(currentContext).stream()
                .map(role -> (String) role.get("name"))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static org.assertj.core.api.ListAssert<String> assertPermissions(Map<String, Object> currentContext) {
        return assertThat(permissions(currentContext));
    }

    @SuppressWarnings("unchecked")
    private static List<String> permissions(Map<String, Object> currentContext) {
        return (List<String>) currentContext.get("permissions");
    }

    @SuppressWarnings("unchecked")
    private static void assertAccountRecord(Map<String, Object> accountRecord, UUID accountId, String roleName) {
        assertThat(accountRecord)
                .containsKeys("id", "email", "displayName", "roles")
                .doesNotContainKeys(
                        "password", "passwordHash", "accessToken", "refreshToken", "tokens", "sessions",
                        "deletedAt", "deletedBy", "createdAt", "createdBy", "updatedAt", "updatedBy"
                );
        assertThat(accountRecord.get("id")).isEqualTo(accountId.toString());
        assertThat(accountRecord.get("roles")).isInstanceOf(List.class);

        List<Map<String, Object>> roles = (List<Map<String, Object>>) accountRecord.get("roles");
        assertThat(roles)
                .singleElement()
                .satisfies(role -> assertThat(role)
                        .containsEntry("name", roleName)
                        .containsKeys("id", "description", "systemManaged"));
    }
}
