package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
