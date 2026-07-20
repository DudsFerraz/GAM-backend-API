package br.org.gam.api.api;

import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

abstract class MemberApiTestSupport extends BaseApiIntegrationTest {

    protected static final String CANONICAL_PHONE = "+5519998877665";
    protected static final String VALID_REASON = "Accepted through the documented Member workflow";
    protected static final String VALID_JUSTIFICATION = "I want to participate in GAM activities";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    private final Set<UUID> domainAccountIds = new LinkedHashSet<>();
    private final List<UUID> customRoleIds = new ArrayList<>();
    private boolean activityFailureTriggerInstalled;

    @AfterEach
    void cleanupMemberFixtures() {
        removeActivityFailureTrigger();

        if (tableExists("membership_solicitations")) {
            for (UUID accountId : domainAccountIds) {
                jdbcTemplate.update("DELETE FROM membership_solicitations WHERE account_id = ?", accountId);
            }
        }

        for (UUID accountId : domainAccountIds) {
            jdbcTemplate.update("DELETE FROM members WHERE account_id = ?", accountId);
        }

        for (UUID roleId : customRoleIds) {
            jdbcTemplate.update("DELETE FROM account_roles WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
            jdbcTemplate.update("DELETE FROM roles WHERE id = ?", roleId);
        }

        customRoleIds.clear();
        domainAccountIds.clear();
    }

    protected AuthSession newSession(String roleName) {
        AuthSession session = registerAndLogin(roleName);
        domainAccountIds.add(session.accountId());
        return session;
    }

    protected AuthSession newSessionWithPermissions(String... permissionCodes) {
        String suffix = UUID.randomUUID().toString();
        String email = "member-api-permissions-" + suffix + "@example.com";
        UUID accountId = newAccount(email, "Permission-scoped caller");
        UUID roleId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(
                "INSERT INTO roles (id, name, description, system_managed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, FALSE, ?, ?)",
                roleId,
                "MAT_" + suffix.substring(0, 32),
                "Test-only permission bundle",
                now,
                now
        );
        customRoleIds.add(roleId);

        for (String permissionCode : permissionCodes) {
            UUID permissionId = jdbcTemplate.queryForObject(
                    "SELECT id FROM permissions WHERE code = ? AND deleted_at IS NULL",
                    UUID.class,
                    permissionCode
            );
            jdbcTemplate.update(
                    "INSERT INTO role_permissions (id, role_id, permission_id, created_at) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(),
                    roleId,
                    permissionId,
                    now
            );
        }

        jdbcTemplate.update(
                "INSERT INTO account_roles (id, account_id, role_id, created_at) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(),
                accountId,
                roleId,
                now
        );

        ExtractableResponse<Response> login = login(email, TEST_PASSWORD);
        return new AuthSession(
                accountId,
                email,
                TEST_PASSWORD,
                login.path("token"),
                login.cookie("refreshToken")
        );
    }

    protected UUID newAccount(String displayName) {
        return newAccount("member-api-" + UUID.randomUUID() + "@example.com", displayName);
    }

    protected UUID newAccount(String email, String displayName) {
        UUID accountId = registerAccount(email, TEST_PASSWORD, displayName);
        domainAccountIds.add(accountId);
        return accountId;
    }

    protected Map<String, Object> memberPayload(UUID accountId, LocalDate birthDate, String reason) {
        return memberPayload(accountId, "Ana", "Silva", birthDate, CANONICAL_PHONE, reason);
    }

    protected Map<String, Object> memberPayload(
            UUID accountId,
            String firstName,
            String surname,
            LocalDate birthDate,
            String phoneNumber,
            String reason
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accountId", accountId.toString());
        payload.put("firstName", firstName);
        payload.put("surname", surname);
        payload.put("birthDate", birthDate == null ? null : birthDate.toString());
        payload.put("phoneNumber", phoneNumber);
        payload.put("reason", reason);
        return payload;
    }

    protected Map<String, Object> solicitationPayload(LocalDate birthDate, String justification) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("firstName", "Ana");
        payload.put("surname", "Silva");
        payload.put("birthDate", birthDate == null ? null : birthDate.toString());
        payload.put("phoneNumber", CANONICAL_PHONE);
        payload.put("justification", justification);
        return payload;
    }

    protected static Map<String, Object> reasonPayload(String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        return payload;
    }

    protected static Map<String, Object> searchPayload(Map<String, Object>... filters) {
        return Map.of("filters", List.of(filters));
    }

    protected static Map<String, Object> filter(String field, Object value, String comparisonMethod) {
        return Map.of(
                "field", field,
                "value", value,
                "comparationMethod", comparisonMethod
        );
    }

    protected UUID registerMember(AuthSession coordinator, UUID accountId) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(memberPayload(accountId, LocalDate.now().minusYears(20), VALID_REASON))
                .post("/members")
                .then()
                .statusCode(201)
                .extract();
        return UUID.fromString(response.path("id"));
    }

    protected UUID submitSolicitation(AuthSession applicant) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(applicant)
                .body(solicitationPayload(LocalDate.now().minusYears(20), VALID_JUSTIFICATION))
                .post("/membership-solicitations")
                .then()
                .statusCode(201)
                .extract();
        return UUID.fromString(response.path("id"));
    }

    protected UUID rejectSolicitation(AuthSession coordinator, UUID solicitationId, String reason) {
        ExtractableResponse<Response> response = authenticatedJsonRequest(coordinator)
                .body(reasonPayload(reason))
                .patch("/membership-solicitations/{id}/reject", solicitationId)
                .then()
                .statusCode(200)
                .extract();
        return UUID.fromString(response.path("id"));
    }

    protected void forceMemberState(UUID memberId, UUID accountId, String status, String lifecycleRole) {
        forceMemberProjection(memberId, accountId, status, lifecycleRole);
    }

    protected void forceMemberProjection(UUID memberId, UUID accountId, String status, String... lifecycleRoles) {
        jdbcTemplate.update("UPDATE members SET status = CAST(? AS member_status_enum) WHERE id = ?", status, memberId);
        jdbcTemplate.update(
                "DELETE FROM account_roles WHERE account_id = ? "
                        + "AND role_id IN (SELECT id FROM roles WHERE name IN ('MEMBER', 'VISITOR', 'COORD'))",
                accountId
        );
        for (String lifecycleRole : lifecycleRoles) {
            grantRole(accountId, lifecycleRole);
        }
    }

    protected UUID newCustomRole(String prefix) {
        UUID roleId = UUID.randomUUID();
        String boundedPrefix = prefix.substring(0, Math.min(prefix.length(), 32));
        String roleName = boundedPrefix + "_" + roleId.toString().substring(0, 8);
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO roles (id, name, description, system_managed, created_at, updated_at) "
                        + "VALUES (?, ?, ?, FALSE, ?, ?)",
                roleId,
                roleName,
                "Member lifecycle custom role fixture",
                now,
                now
        );
        customRoleIds.add(roleId);
        return roleId;
    }

    protected long activeRoleAssignmentCount(UUID accountId, String roleName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_roles ar JOIN roles r ON r.id = ar.role_id "
                        + "WHERE ar.account_id = ? AND r.name = ? "
                        + "AND ar.deleted_at IS NULL AND r.deleted_at IS NULL",
                Long.class,
                accountId,
                roleName
        );
    }

    protected void clearActivities() {
        jdbcTemplate.update("DELETE FROM activity_logs");
    }

    protected void failActivityWritesFor(String action) {
        if (!action.matches("[A-Z_]+")) {
            throw new IllegalArgumentException("Activity action must use the persisted enum-style vocabulary.");
        }
        removeActivityFailureTrigger();
        jdbcTemplate.execute(("""
                CREATE OR REPLACE FUNCTION fail_selected_test_activity() RETURNS trigger AS $$
                BEGIN
                    IF NEW.action = '%s' THEN
                        RAISE EXCEPTION 'forced activity persistence failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """).formatted(action));
        activityFailureTriggerInstalled = true;
        try {
            jdbcTemplate.execute("""
                    CREATE TRIGGER fail_selected_test_activity_trigger
                    BEFORE INSERT ON activity_logs
                    FOR EACH ROW EXECUTE FUNCTION fail_selected_test_activity()
                    """);
        } catch (RuntimeException exception) {
            removeActivityFailureTrigger();
            throw exception;
        }
    }

    protected void removeActivityFailureTrigger() {
        if (!activityFailureTriggerInstalled) {
            return;
        }
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_selected_test_activity_trigger ON activity_logs");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_selected_test_activity()");
        activityFailureTriggerInstalled = false;
    }

    protected void softDeleteMember(UUID memberId) {
        jdbcTemplate.update("UPDATE members SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", memberId);
    }

    protected long memberCount(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM members WHERE account_id = ? AND deleted_at IS NULL",
                Long.class,
                accountId
        );
    }

    protected String memberStatus(UUID memberId) {
        return jdbcTemplate.queryForObject(
                "SELECT status::text FROM members WHERE id = ? AND deleted_at IS NULL",
                String.class,
                memberId
        );
    }

    protected Set<String> activeRoleNames(UUID accountId) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
                "SELECT r.name FROM account_roles ar JOIN roles r ON r.id = ar.role_id "
                        + "WHERE ar.account_id = ? AND ar.deleted_at IS NULL AND r.deleted_at IS NULL",
                String.class,
                accountId
        ));
    }

    protected UUID roleId(String roleName) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = ? AND deleted_at IS NULL",
                UUID.class,
                roleName
        );
    }

    protected long activityCount(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE action = ?",
                Long.class,
                action
        );
    }

    protected long allLifecycleActivityCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE action IN ("
                        + "'MEMBER_REGISTERED', 'MEMBER_ACTIVATED', 'MEMBER_DEACTIVATED', "
                        + "'COORDINATOR_GRANTED', 'COORDINATOR_REVOKED', "
                        + "'MEMBERSHIP_SOLICITATION_SUBMITTED', 'MEMBERSHIP_SOLICITATION_APPROVED', "
                        + "'MEMBERSHIP_SOLICITATION_REJECTED', 'ACCOUNT_ROLE_ADDED', 'ACCOUNT_ROLE_REMOVED')",
                Long.class
        );
    }

    protected Map<String, Object> activity(String action) {
        return jdbcTemplate.queryForMap(
                "SELECT actor_account_id, target_id, reason, metadata, request_id, ip_address, user_agent "
                        + "FROM activity_logs WHERE action = ?",
                action
        );
    }

    protected long solicitationCount(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membership_solicitations WHERE account_id = ?",
                Long.class,
                accountId
        );
    }

    protected long pendingSolicitationCount(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membership_solicitations "
                        + "WHERE account_id = ? AND status::text = 'PENDING' AND deleted_at IS NULL",
                Long.class,
                accountId
        );
    }

    protected String solicitationStatus(UUID solicitationId) {
        return jdbcTemplate.queryForObject(
                "SELECT status::text FROM membership_solicitations WHERE id = ? AND deleted_at IS NULL",
                String.class,
                solicitationId
        );
    }

    protected static List<UUID> resourceIds(List<Map<String, Object>> records) {
        return records.stream()
                .map(record -> UUID.fromString((String) record.get("id")))
                .toList();
    }

    @SuppressWarnings("unchecked")
    protected static void assertMemberRecord(
            Map<String, Object> record,
            UUID memberId,
            UUID accountId,
            String accountEmail,
            String accountDisplayName,
            String status
    ) {
        assertThat(record).containsOnlyKeys(
                "id", "firstName", "surname", "birthDate", "phoneNumber", "status", "account"
        );
        assertThat(record)
                .containsEntry("id", memberId.toString())
                .containsEntry("firstName", "Ana")
                .containsEntry("surname", "Silva")
                .containsEntry("phoneNumber", CANONICAL_PHONE)
                .containsEntry("status", status);

        Map<String, Object> account = (Map<String, Object>) record.get("account");
        assertThat(account).containsOnlyKeys("id", "email", "displayName");
        assertThat(account)
                .containsEntry("id", accountId.toString())
                .containsEntry("email", accountEmail)
                .containsEntry("displayName", accountDisplayName);
    }

    @SuppressWarnings("unchecked")
    protected static void assertSolicitationRecord(
            Map<String, Object> record,
            UUID solicitationId,
            AuthSession applicant,
            String status
    ) {
        assertThat(record).containsOnlyKeys(
                "id", "account", "firstName", "surname", "birthDate", "phoneNumber", "justification",
                "status", "submittedAt", "reviewedBy", "decidedAt", "reviewReason", "memberId"
        );
        assertThat(record)
                .containsEntry("id", solicitationId.toString())
                .containsEntry("firstName", "Ana")
                .containsEntry("surname", "Silva")
                .containsEntry("phoneNumber", CANONICAL_PHONE)
                .containsEntry("justification", VALID_JUSTIFICATION)
                .containsEntry("status", status);
        assertThat(record.get("submittedAt")).isNotNull();

        Map<String, Object> account = (Map<String, Object>) record.get("account");
        assertThat(account).containsOnlyKeys("id", "email", "displayName");
        assertThat(account)
                .containsEntry("id", applicant.accountId().toString())
                .containsEntry("email", applicant.email());
    }

    protected static void assertUuidV7(UUID id) {
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = ?)",
                Boolean.class,
                tableName
        );
        return Boolean.TRUE.equals(exists);
    }
}
