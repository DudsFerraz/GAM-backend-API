package br.org.gam.api.api;

import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@PersistenceTest
@SecurityTest
@DisplayName("API - Explicit Member Account linking")
class MemberAccountLinkApiIT extends MemberApiTestSupport {

    private final List<UUID> importedMembers = new ArrayList<>();
    private final List<UUID> importBatches = new ArrayList<>();
    private final List<UUID> customRoles = new ArrayList<>();

    @AfterEach
    void cleanupLinkFixtures() {
        for (UUID memberId : importedMembers) {
            jdbcTemplate.update("DELETE FROM member_experiences WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM member_sacraments WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM member_contribution_areas WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM member_other_contribution_areas WHERE member_id = ?", memberId);
            jdbcTemplate.update("DELETE FROM members WHERE id = ?", memberId);
        }
        for (UUID roleId : customRoles) {
            jdbcTemplate.update("DELETE FROM account_roles WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM roles WHERE id = ?", roleId);
        }
        for (UUID batchId : importBatches) {
            jdbcTemplate.update("DELETE FROM member_information_import_batches WHERE id = ?", batchId);
        }
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-003/004/005 - inactive link -> VISITOR, custom and SUDO preserved, version and one audit advance")
    void inactiveLinkShouldCommitCompleteProjectionAndPreserveUnrelatedRoles() {
        AuthSession coordinator = newSession("COORD");
        UUID memberId = insertAccountlessMember("INACTIVE");
        UUID accountId = newAccount("Synthetic inactive link target");
        UUID customRoleId = insertCustomRole("SYNTHETIC_LINK_ROLE_" + UUID.randomUUID().toString().substring(0, 8));
        assignRole(accountId, customRoleId);
        assignRole(accountId, roleId("SUDO"));
        long versionBefore = memberVersion(memberId);
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(Map.of("accountId", accountId.toString(), "reason", "  Synthetic identity confirmation  "))
                .patch("/members/{memberId}/account/link", memberId).then().extract();

        assertThat(response.statusCode()).as(response.asString()).isEqualTo(204);
        assertThat(linkedAccount(memberId)).isEqualTo(accountId);
        assertThat(activeRoleNames(accountId)).containsExactlyInAnyOrder("VISITOR", "SUDO",
                jdbcTemplate.queryForObject("SELECT name FROM roles WHERE id = ?", String.class, customRoleId));
        assertThat(memberVersion(memberId)).isGreaterThan(versionBefore);
        assertThat(activityCount("MEMBER_ACCOUNT_LINKED")).isEqualTo(1);
        assertThat(activity("MEMBER_ACCOUNT_LINKED").get("metadata").toString())
                .contains(accountId.toString(), "VISITOR")
                .doesNotContain("Synthetic Account-less", "@example.com");
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-016 - link security, visibility, and immutable one-to-one conflicts -> exact status family")
    void linkShouldEnforceAuthenticationAuthorizationVisibilityAndImmutableConflicts() {
        AuthSession coordinator = newSession("COORD");
        AuthSession visitor = newSession("VISITOR");
        UUID eligibleMember = insertAccountlessMember("ACTIVE");
        UUID eligibleAccount = newAccount("Synthetic secure link account");
        Map<String, Object> body = Map.of("accountId", eligibleAccount.toString(), "reason", "Synthetic identity confirmation");

        assertThat(jsonRequest().body(body).patch("/members/{memberId}/account/link", eligibleMember).statusCode())
                .isEqualTo(401);
        assertThat(authenticatedJsonRequest(visitor).body(body)
                .patch("/members/{memberId}/account/link", eligibleMember).statusCode()).isEqualTo(403);
        assertThat(authenticatedJsonRequest(coordinator).body(body)
                .patch("/members/{memberId}/account/link", UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(authenticatedJsonRequest(coordinator)
                .body(Map.of("accountId", UUID.randomUUID().toString(), "reason", "Synthetic identity confirmation"))
                .patch("/members/{memberId}/account/link", eligibleMember).statusCode()).isEqualTo(404);

        authenticatedJsonRequest(coordinator).body(body)
                .patch("/members/{memberId}/account/link", eligibleMember).then().statusCode(204);
        UUID anotherAccount = newAccount("Synthetic repeated link account");
        assertThat(authenticatedJsonRequest(coordinator)
                .body(Map.of("accountId", anotherAccount.toString(), "reason", "Synthetic identity confirmation"))
                .patch("/members/{memberId}/account/link", eligibleMember).statusCode()).isEqualTo(409);

        UUID anotherMember = insertAccountlessMember("ACTIVE");
        assertThat(authenticatedJsonRequest(coordinator).body(body)
                .patch("/members/{memberId}/account/link", anotherMember).statusCode()).isEqualTo(409);
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-004/016 - lifecycle Role conflict blocks link; rejected history alone does not")
    void lifecycleRoleConflictShouldFailClosedWhileRejectedHistoryDoesNotBlock() {
        AuthSession coordinator = newSession("COORD");
        UUID blockedMember = insertAccountlessMember("ACTIVE");
        UUID blockedAccount = newAccount("Synthetic lifecycle conflict");
        assignRole(blockedAccount, roleId("MEMBER"));

        assertThat(authenticatedJsonRequest(coordinator)
                .body(Map.of("accountId", blockedAccount.toString(), "reason", "Synthetic identity confirmation"))
                .patch("/members/{memberId}/account/link", blockedMember).statusCode()).isEqualTo(409);
        assertThat(linkedAccount(blockedMember)).isNull();

        UUID eligibleMember = insertAccountlessMember("ACTIVE");
        AuthSession rejectedApplicant = newSession("VISITOR");
        submitSolicitation(rejectedApplicant);
        UUID solicitationId = jdbcTemplate.queryForObject(
                "SELECT id FROM membership_solicitations WHERE account_id = ?", UUID.class, rejectedApplicant.accountId());
        jdbcTemplate.update("UPDATE membership_solicitations SET status = CAST('REJECTED' AS membership_solicitation_status_enum), "
                        + "reviewed_by_account_id = ?, review_reason = 'Synthetic rejected history', "
                        + "decided_at = CURRENT_TIMESTAMP WHERE id = ?",
                coordinator.accountId(), solicitationId);
        jdbcTemplate.update("DELETE FROM account_roles WHERE account_id = ? "
                        + "AND role_id = (SELECT id FROM roles WHERE name = 'VISITOR' AND deleted_at IS NULL)",
                rejectedApplicant.accountId());

        assertThat(authenticatedJsonRequest(coordinator)
                .body(Map.of("accountId", rejectedApplicant.accountId().toString(),
                        "reason", "Synthetic identity confirmation"))
                .patch("/members/{memberId}/account/link", eligibleMember).statusCode()).isEqualTo(204);
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-005/016 - activity failure and competing links -> rollback and exactly one winner")
    void linkShouldRollbackOnAuditFailureAndSerializeCompetingAccounts() throws Exception {
        AuthSession coordinator = newSession("COORD");
        UUID rollbackMember = insertAccountlessMember("ACTIVE");
        UUID rollbackAccount = newAccount("Synthetic rollback link account");
        failActivityWritesFor("MEMBER_ACCOUNT_LINKED");
        try {
            assertThat(authenticatedJsonRequest(coordinator)
                    .body(Map.of("accountId", rollbackAccount.toString(), "reason", "Synthetic identity confirmation"))
                    .patch("/members/{memberId}/account/link", rollbackMember).statusCode()).isEqualTo(500);
        } finally {
            removeActivityFailureTrigger();
        }
        assertThat(linkedAccount(rollbackMember)).isNull();
        assertThat(activeRoleNames(rollbackAccount)).doesNotContain("MEMBER", "VISITOR");

        UUID competingMember = insertAccountlessMember("ACTIVE");
        UUID firstAccount = newAccount("Synthetic competing link one");
        UUID secondAccount = newAccount("Synthetic competing link two");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> linkStatus(coordinator, competingMember, firstAccount, start));
            Future<Integer> second = executor.submit(() -> linkStatus(coordinator, competingMember, secondAccount, start));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(204, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(Set.of(firstAccount, secondAccount)).contains(linkedAccount(competingMember));
        assertThat(activityCount("MEMBER_ACCOUNT_LINKED")).isEqualTo(1);
    }

    private int linkStatus(AuthSession caller, UUID memberId, UUID accountId, CountDownLatch start) throws Exception {
        start.await();
        return authenticatedJsonRequest(caller)
                .body(Map.of("accountId", accountId.toString(), "reason", "Synthetic competing identity confirmation"))
                .patch("/members/{memberId}/account/link", memberId).statusCode();
    }

    private UUID insertAccountlessMember(String status) {
        UUID batchId = UUIDGenerator.generateUUIDV7();
        UUID memberId = UUIDGenerator.generateUUIDV7();
        importBatches.add(batchId);
        importedMembers.add(memberId);
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("INSERT INTO member_information_import_batches "
                        + "(id, survey_cycle, dataset_checksum, imported_member_count, imported_response_count, executed_at, reason) "
                        + "VALUES (?, 2026, ?, 1, 0, ?, 'Synthetic API fixture')",
                batchId, "sha256:" + batchId.toString().replace("-", "").repeat(2), now);
        jdbcTemplate.update("INSERT INTO members (id, account_id, first_name, surname, birth_date, gam_entry_date, "
                        + "residential_city, phone_number, contact_email, dietary_restriction_status, status, import_batch_id, "
                        + "version, created_at, updated_at) VALUES (?, NULL, 'Synthetic', 'Accountless', DATE '2000-01-01', "
                        + "DATE '2020-01-01', 'Synthetic City', '+5519998877665', ?, "
                        + "CAST('NOT_INFORMED' AS member_information_status_enum), CAST(? AS member_status_enum), ?, 0, ?, ?)",
                memberId, "accountless-" + memberId + "@example.com", status, batchId, now, now);
        return memberId;
    }

    private UUID insertCustomRole(String name) {
        UUID roleId = UUIDGenerator.generateUUIDV7();
        customRoles.add(roleId);
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("INSERT INTO roles (id, name, description, system_managed, created_at, updated_at) "
                        + "VALUES (?, ?, 'Synthetic link-preservation role', false, ?, ?)", roleId, name, now, now);
        return roleId;
    }

    private void assignRole(UUID accountId, UUID roleId) {
        jdbcTemplate.update("INSERT INTO account_roles (id, account_id, role_id, created_at) VALUES (?, ?, ?, ?)",
                UUIDGenerator.generateUUIDV7(), accountId, roleId, Timestamp.from(Instant.now()));
    }

    private UUID linkedAccount(UUID memberId) {
        return jdbcTemplate.queryForObject("SELECT account_id FROM members WHERE id = ?", UUID.class, memberId);
    }

    private long memberVersion(UUID memberId) {
        return jdbcTemplate.queryForObject("SELECT version FROM members WHERE id = ?", Long.class, memberId);
    }
}
