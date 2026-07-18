package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

@ApiTest
@FunctionalTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Member Visibility and Search")
class MemberVisibilityApiIT extends MemberApiTestSupport {

    @ParameterizedTest(name = "{0}")
    @MethodSource("memberStatuses")
    @DisplayName("REQ-MEMBER-009 and REQ-MEMBER-013 - linked Account reads its own Member without general read permissions")
    void linkedAccountShouldReadItsOwnMemberWithoutGeneralReadPermissions(
            String scenario,
            String status,
            String lifecycleRole
    ) {
        AuthSession coordinator = newSession("COORD");
        AuthSession linkedAccount = newSessionWithPermissions();
        UUID memberId = registerMember(coordinator, linkedAccount.accountId());
        forceMemberState(memberId, linkedAccount.accountId(), status, lifecycleRole);

        ExtractableResponse<Response> response = authenticatedJsonRequest(linkedAccount)
                .get("/members/{id}", memberId)
                .then()
                .statusCode(200)
                .extract();

        assertMemberRecord(
                response.jsonPath().getMap("$"),
                memberId,
                linkedAccount.accountId(),
                linkedAccount.email(),
                "Permission-scoped caller",
                status
        );
    }

    @Test
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-013 - MEMBER_GET_NON_ACTIVE without MEMBER_GET -> HTTP 403")
    void nonActiveVisibilityWithoutMemberGetShouldNotGrantDirectLookup() {
        AuthSession coordinator = newSession("COORD");
        AuthSession nonActiveOnlyCaller = newSessionWithPermissions("MEMBER_GET_NON_ACTIVE");
        UUID targetAccountId = newAccount("Direct lookup target");
        UUID memberId = registerMember(coordinator, targetAccountId);
        forceMemberState(memberId, targetAccountId, "ACTIVE", "MEMBER");

        authenticatedJsonRequest(nonActiveOnlyCaller)
                .get("/members/{id}", memberId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-013 - another inactive Member without read permissions -> HTTP 403")
    void inactiveMemberWithoutReadPermissionsShouldReturnForbidden() {
        AuthSession coordinator = newSession("COORD");
        AuthSession callerWithoutPermissions = newSessionWithPermissions();
        UUID targetAccountId = newAccount("Inactive capability-denial target");
        UUID memberId = registerMember(coordinator, targetAccountId);
        forceMemberState(memberId, targetAccountId, "INACTIVE", "VISITOR");

        authenticatedJsonRequest(callerWithoutPermissions)
                .get("/members/{id}", memberId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-013 - missing Member without read permissions -> HTTP 404")
    void missingMemberWithoutReadPermissionsShouldReturnNotFound() {
        AuthSession callerWithoutPermissions = newSessionWithPermissions();

        authenticatedJsonRequest(callerWithoutPermissions)
                .get("/members/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-013 - another inactive Member without MEMBER_GET_NON_ACTIVE -> hidden as HTTP 404")
    void inactiveMemberWithoutNonActiveVisibilityShouldBeHidden() {
        AuthSession coordinator = newSession("COORD");
        AuthSession activeOnlyReader = newSessionWithPermissions("MEMBER_GET");
        UUID targetAccountId = newAccount("Inactive lookup target");
        UUID memberId = registerMember(coordinator, targetAccountId);
        forceMemberState(memberId, targetAccountId, "INACTIVE", "VISITOR");

        authenticatedJsonRequest(activeOnlyReader)
                .get("/members/{id}", memberId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("REQ-MEMBER-009 and REQ-MEMBER-013 - MEMBER_GET plus MEMBER_GET_NON_ACTIVE -> inactive Member record")
    void completeDirectLookupPermissionsShouldExposeAnotherInactiveMember() {
        AuthSession coordinator = newSession("COORD");
        AuthSession inactiveReader = newSessionWithPermissions("MEMBER_GET", "MEMBER_GET_NON_ACTIVE");
        String targetEmail = "inactive-visible-" + UUID.randomUUID() + "@example.com";
        UUID targetAccountId = newAccount(targetEmail, "Visible inactive target");
        UUID memberId = registerMember(coordinator, targetAccountId);
        forceMemberState(memberId, targetAccountId, "INACTIVE", "VISITOR");

        ExtractableResponse<Response> response = authenticatedJsonRequest(inactiveReader)
                .get("/members/{id}", memberId)
                .then()
                .statusCode(200)
                .extract();

        assertMemberRecord(
                response.jsonPath().getMap("$"),
                memberId,
                targetAccountId,
                targetEmail,
                "Visible inactive target",
                "INACTIVE"
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("insufficientSearchCapabilities")
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-014 - linked access and non-search permissions do not grant Member search")
    void memberSearchShouldRequireMemberSearchForEveryCaller(String scenario, String[] permissions) {
        AuthSession coordinator = newSession("COORD");
        AuthSession linkedAccount = newSessionWithPermissions(permissions);
        UUID memberId = registerMember(coordinator, linkedAccount.accountId());

        authenticatedJsonRequest(linkedAccount)
                .body(searchPayload(filter("id", memberId.toString(), "EQUALS")))
                .post("/members/search")
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-MEMBER-010 and REQ-MEMBER-014 - inactive status filter cannot bypass visibility")
    void inactiveStatusFilterShouldNotBypassSearchVisibility() {
        AuthSession coordinator = newSession("COORD");
        AuthSession activeOnlySearcher = newSessionWithPermissions("MEMBER_SEARCH");
        UUID targetAccountId = newAccount("Hidden inactive search target");
        UUID inactiveMemberId = registerMember(coordinator, targetAccountId);
        forceMemberState(inactiveMemberId, targetAccountId, "INACTIVE", "VISITOR");

        ExtractableResponse<Response> response = authenticatedJsonRequest(activeOnlySearcher)
                .body(searchPayload(filter("status", "INACTIVE", "EQUALS")))
                .post("/members/search?size=100")
                .then()
                .statusCode(200)
                .extract();

        assertThat(resourceIds(response.jsonPath().getList("items"))).doesNotContain(inactiveMemberId);
    }

    @Test
    @DisplayName("REQ-MEMBER-009 and REQ-MEMBER-014 - MEMBER_SEARCH plus MEMBER_GET_NON_ACTIVE -> inactive search result")
    void completeSearchPermissionsShouldExposeInactiveMembers() {
        AuthSession coordinator = newSession("COORD");
        AuthSession inactiveSearcher = newSessionWithPermissions("MEMBER_SEARCH", "MEMBER_GET_NON_ACTIVE");
        String targetEmail = "inactive-search-" + UUID.randomUUID() + "@example.com";
        UUID targetAccountId = newAccount(targetEmail, "Visible inactive search target");
        UUID inactiveMemberId = registerMember(coordinator, targetAccountId);
        forceMemberState(inactiveMemberId, targetAccountId, "INACTIVE", "VISITOR");

        ExtractableResponse<Response> response = authenticatedJsonRequest(inactiveSearcher)
                .body(searchPayload(filter("id", inactiveMemberId.toString(), "EQUALS")))
                .post("/members/search")
                .then()
                .statusCode(200)
                .extract();

        List<Map<String, Object>> records = response.jsonPath().getList("items");
        assertThat(records).hasSize(1);
        assertMemberRecord(
                records.getFirst(),
                inactiveMemberId,
                targetAccountId,
                targetEmail,
                "Visible inactive search target",
                "INACTIVE"
        );
    }

    @Test
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-015 - unauthenticated presence-history request -> HTTP 401")
    void memberPresenceHistoryShouldRejectUnauthenticatedRequests() {
        jsonRequest()
                .get("/members/{memberId}/presences", UUID.randomUUID())
                .then()
                .statusCode(401)
                .body("status", equalTo(401));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("memberStatuses")
    @DisplayName("REQ-MEMBER-015 - linked Account reads its own paginated presence history without general permissions")
    void linkedAccountShouldReadItsOwnPresenceHistoryWithoutGeneralPermissions(
            String scenario,
            String status,
            String lifecycleRole
    ) {
        AuthSession coordinator = newSession("COORD");
        AuthSession linkedAccount = newSessionWithPermissions();
        UUID memberId = registerMember(coordinator, linkedAccount.accountId());
        forceMemberState(memberId, linkedAccount.accountId(), status, lifecycleRole);

        ExtractableResponse<Response> response = authenticatedJsonRequest(linkedAccount)
                .get("/members/{memberId}/presences", memberId)
                .then()
                .statusCode(200)
                .extract();

        assertEmptyPage(response);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("authorizedPresenceCapabilities")
    @DisplayName("REQ-MEMBER-015 - required presence and status permissions -> paginated presence history")
    void requiredPresenceCapabilitiesShouldExposeAnotherMembersHistory(
            String scenario,
            String status,
            String lifecycleRole,
            String[] permissions
    ) {
        AuthSession coordinator = newSession("COORD");
        AuthSession caller = newSessionWithPermissions(permissions);
        UUID targetAccountId = newAccount("Visible presence-history target");
        UUID memberId = registerMember(coordinator, targetAccountId);
        forceMemberState(memberId, targetAccountId, status, lifecycleRole);

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .get("/members/{memberId}/presences", memberId)
                .then()
                .statusCode(200)
                .extract();

        assertEmptyPage(response);
    }

    @Test
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-015 - PRESENCES_SEARCH without inactive visibility -> hidden as HTTP 404")
    void presenceSearchShouldNotBypassInactiveMemberVisibility() {
        AuthSession coordinator = newSession("COORD");
        AuthSession presenceSearcher = newSessionWithPermissions("PRESENCES_SEARCH");
        UUID targetAccountId = newAccount("Hidden inactive presence target");
        UUID memberId = registerMember(coordinator, targetAccountId);
        forceMemberState(memberId, targetAccountId, "INACTIVE", "VISITOR");

        authenticatedJsonRequest(presenceSearcher)
                .get("/members/{memberId}/presences", memberId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("insufficientPresenceCapabilities")
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-015 - visible Member without PRESENCES_SEARCH -> HTTP 403")
    void visibleMemberWithoutPresenceSearchShouldReturnForbidden(String scenario, String[] permissions) {
        AuthSession coordinator = newSession("COORD");
        AuthSession caller = newSessionWithPermissions(permissions);
        UUID targetAccountId = newAccount("Forbidden presence-history target");
        UUID memberId = registerMember(coordinator, targetAccountId);
        forceMemberState(memberId, targetAccountId, "ACTIVE", "MEMBER");

        authenticatedJsonRequest(caller)
                .get("/members/{memberId}/presences", memberId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-015 - inactive visibility without PRESENCES_SEARCH -> HTTP 403")
    void inactiveVisibilityWithoutPresenceSearchShouldReturnForbidden() {
        AuthSession coordinator = newSession("COORD");
        AuthSession nonActiveOnlyCaller = newSessionWithPermissions("MEMBER_GET_NON_ACTIVE");
        UUID targetAccountId = newAccount("Visible inactive presence target");
        UUID memberId = registerMember(coordinator, targetAccountId);
        forceMemberState(memberId, targetAccountId, "INACTIVE", "VISITOR");

        authenticatedJsonRequest(nonActiveOnlyCaller)
                .get("/members/{memberId}/presences", memberId)
                .then()
                .statusCode(403)
                .body("status", equalTo(403));
    }

    @Test
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-015 - missing Member presence history -> HTTP 404")
    void missingMemberPresenceHistoryShouldReturnNotFound() {
        AuthSession caller = newSessionWithPermissions("PRESENCES_SEARCH", "MEMBER_GET_NON_ACTIVE");

        authenticatedJsonRequest(caller)
                .get("/members/{memberId}/presences", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    @Test
    @DisplayName("REQ-MEMBER-011 and REQ-MEMBER-015 - soft-deleted Member presence history -> HTTP 404")
    void softDeletedMemberPresenceHistoryShouldReturnNotFound() {
        AuthSession coordinator = newSession("COORD");
        AuthSession caller = newSessionWithPermissions("PRESENCES_SEARCH", "MEMBER_GET_NON_ACTIVE");

        UUID targetAccountId = newAccount("Deleted presence-history target");
        UUID memberId = registerMember(coordinator, targetAccountId);
        softDeleteMember(memberId);

        authenticatedJsonRequest(caller)
                .get("/members/{memberId}/presences", memberId)
                .then()
                .statusCode(404)
                .body("status", equalTo(404));
    }

    private static void assertEmptyPage(ExtractableResponse<Response> response) {
        Map<String, Object> page = response.jsonPath().getMap("$");
        assertThat(page).containsOnlyKeys(
                "items", "page", "size", "totalElements", "totalPages", "first", "last"
        );
        assertThat(response.jsonPath().getList("items")).isEmpty();
        assertThat(response.jsonPath().getInt("page")).isZero();
        assertThat(response.jsonPath().getLong("totalElements")).isZero();
    }

    private static Stream<Arguments> memberStatuses() {
        return Stream.of(
                Arguments.of("ACTIVE Member", "ACTIVE", "MEMBER"),
                Arguments.of("INACTIVE Member", "INACTIVE", "VISITOR")
        );
    }

    private static Stream<Arguments> insufficientSearchCapabilities() {
        return Stream.of(
                Arguments.of("linked Account only", new String[]{}),
                Arguments.of("MEMBER_GET", new String[]{"MEMBER_GET"}),
                Arguments.of("MEMBER_GET_NON_ACTIVE", new String[]{"MEMBER_GET_NON_ACTIVE"})
        );
    }

    private static Stream<Arguments> authorizedPresenceCapabilities() {
        return Stream.of(
                Arguments.of(
                        "ACTIVE with PRESENCES_SEARCH",
                        "ACTIVE",
                        "MEMBER",
                        new String[]{"PRESENCES_SEARCH"}
                ),
                Arguments.of(
                        "INACTIVE with PRESENCES_SEARCH and MEMBER_GET_NON_ACTIVE",
                        "INACTIVE",
                        "VISITOR",
                        new String[]{"PRESENCES_SEARCH", "MEMBER_GET_NON_ACTIVE"}
                )
        );
    }

    private static Stream<Arguments> insufficientPresenceCapabilities() {
        return Stream.of(
                Arguments.of("no permissions", new String[]{}),
                Arguments.of("MEMBER_GET does not substitute", new String[]{"MEMBER_GET"}),
                Arguments.of("MEMBER_SEARCH does not substitute", new String[]{"MEMBER_SEARCH"})
        );
    }
}
