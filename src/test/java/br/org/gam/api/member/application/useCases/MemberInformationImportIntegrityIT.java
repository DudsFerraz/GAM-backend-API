package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.persistence.AnnualMemberInformationResponseRepository;
import br.org.gam.api.member.persistence.MemberInformationImportBatchRepository;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@IntegrationTest
@PersistenceTest
@FunctionalTest
@DisplayName("Persistence - Member information import deep idempotency")
class MemberInformationImportIntegrityIT extends PostgreSQLIntegrationTest {

    @Autowired ObjectMapper mapper;
    @Autowired MemberInformationImportBatchRepository batches;
    @Autowired MemberRepository members;
    @Autowired AnnualMemberInformationResponseRepository responses;
    @Autowired ActivityEvents activityEvents;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-009/015 - first apply without Account -> one committed Developer import activity")
    void firstApplyShouldCommitOneDeveloperActivityWithoutAuthenticatedAccount(@TempDir Path directory)
            throws Exception {
        ObjectNode document = MemberInformationImportJobTest.syntheticApprovedDocument(mapper, 74);
        String checksum = MemberInformationImportJobTest.checksum(mapper, document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum);
        Path input = directory.resolve("synthetic-first-apply-audit.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        UUID batchId = UUID.fromString("01970000-0000-7000-8000-000000000001");
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getBeansOfType(ExitCodeGenerator.class)).thenReturn(Map.of());
        MemberInformationImportJob job = new MemberInformationImportJob(
                mapper, batches, members, responses, activityEvents, context);

        cleanupImport(batchId);
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                try {
                    job.run(new DefaultApplicationArguments(
                            "--maintenance.action=apply", "--maintenance.file=" + input,
                            "--maintenance.actor-reference=synthetic-maintenance-operator",
                            "--maintenance.reason=Synthetic reviewed member information import"
                    ));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                try {
                    job.run(new DefaultApplicationArguments(
                            "--maintenance.action=apply", "--maintenance.file=" + input,
                            "--maintenance.actor-reference=synthetic-rerun-operator",
                            "--maintenance.reason=Different synthetic rerun reason"
                    ));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM member_information_import_batches WHERE id = ?", Long.class, batchId))
                    .isEqualTo(1L);
            Map<String, Object> batch = jdbcTemplate.queryForMap(
                    "SELECT survey_cycle, imported_member_count, imported_response_count, reason "
                            + "FROM member_information_import_batches WHERE id = ?",
                    batchId
            );
            assertThat(batch)
                    .containsEntry("survey_cycle", 2026)
                    .containsEntry("imported_member_count", 74)
                    .containsEntry("imported_response_count", 74)
                    .containsEntry("reason", "Synthetic reviewed member information import");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM members WHERE import_batch_id = ?", Long.class, batchId))
                    .isEqualTo(74L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM annual_member_information_responses WHERE import_batch_id = ?",
                    Long.class, batchId))
                    .isEqualTo(74L);

            List<Map<String, Object>> activities = jdbcTemplate.queryForList(
                    "SELECT actor_kind, actor_account_id, actor_reference, target_type, target_id, target_scope, "
                            + "action, reason, metadata, request_id FROM activity_logs "
                            + "WHERE target_id = ? AND action = 'MEMBER_INFORMATION_IMPORTED'",
                    batchId
            );
            assertThat(activities).singleElement().satisfies(activity -> {
                assertThat(activity)
                        .containsEntry("actor_kind", "DEVELOPER")
                        .containsEntry("actor_reference", "synthetic-maintenance-operator")
                        .containsEntry("target_type", "MEMBER_INFORMATION_IMPORT_BATCH")
                        .containsEntry("target_id", batchId)
                        .containsEntry("action", "MEMBER_INFORMATION_IMPORTED")
                        .containsEntry("reason", "Synthetic reviewed member information import");
                assertThat(activity.get("actor_account_id")).isNull();
                assertThat(activity.get("target_scope")).isNull();
                assertThat(activity.get("request_id")).isNull();
                assertThat(readJson(activity.get("metadata"))).isEqualTo(mapper.createObjectNode()
                        .put("surveyCycle", 2026)
                        .put("memberCount", 74)
                        .put("responseCount", 74));
            });
        } finally {
            SecurityContextHolder.clearContext();
            RequestContextHolder.resetRequestAttributes();
            cleanupImport(batchId);
        }
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-014/015 - duplicate import activity -> corrupted batch, not successful no-op")
    void idempotentRerunShouldRejectExtraImportActivity(@TempDir Path directory) throws Exception {
        ObjectNode document = MemberInformationImportJobTest.syntheticApprovedDocument(mapper, 74);
        String checksum = MemberInformationImportJobTest.checksum(mapper, document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum);
        Path input = directory.resolve("synthetic-extra-activity-corruption.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        UUID batchId = UUID.fromString("01970000-0000-7000-8000-000000000001");
        Timestamp now = Timestamp.from(Instant.now());
        try {
            ActivityEvents activities = mock(ActivityEvents.class);
            ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
            when(context.getBeansOfType(ExitCodeGenerator.class)).thenReturn(Map.of());
            MemberInformationImportJob job = new MemberInformationImportJob(
                    mapper, batches, members, responses, activities, context);

            new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                try {
                    job.run(new DefaultApplicationArguments(
                            "--maintenance.action=apply", "--maintenance.file=" + input,
                            "--maintenance.actor-reference=synthetic-integrity-operator",
                            "--maintenance.reason=Synthetic integrity first apply"
                    ));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            insertImportActivity(batchId, now);
            insertImportActivity(batchId, now);

            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                try {
                    job.run(new DefaultApplicationArguments(
                            "--maintenance.action=apply", "--maintenance.file=" + input,
                            "--maintenance.actor-reference=synthetic-integrity-operator",
                            "--maintenance.reason=Synthetic integrity rerun"
                    ));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .rootCause()
                    .hasMessage("member-info-import PARTIAL_OR_CORRUPTED_BATCH recordIndex=-1 field=batch.id");
        } finally {
            cleanupImport(batchId);
        }
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-013/014 - explicit UUID matching a retained soft-deleted Member -> safe collision without writes")
    void softDeletedMemberIdentifierShouldFailValidationWithoutPersistenceWrites(@TempDir Path directory)
            throws Exception {
        ObjectNode document = MemberInformationImportJobTest.syntheticApprovedDocument(mapper, 74);
        UUID batchId = UUID.fromString(document.path("batch").path("id").asText());
        UUID softDeletedMemberId = UUID.fromString("01970000-0000-7000-8000-000000000099");
        ((ObjectNode) document.path("records").path(0).path("member"))
                .put("id", softDeletedMemberId.toString());
        ((ObjectNode) document.path("records").path(0).path("annualResponse"))
                .put("memberId", softDeletedMemberId.toString());
        ((ObjectNode) document.path("batch")).put(
                "datasetChecksum", MemberInformationImportJobTest.checksum(mapper, document));
        Path input = directory.resolve("synthetic-soft-deleted-member-collision.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getBeansOfType(ExitCodeGenerator.class)).thenReturn(Map.of());
        MemberInformationImportJob job = new MemberInformationImportJob(
                mapper, batches, members, responses, activityEvents, context);

        cleanupImport(batchId);
        deleteMember(softDeletedMemberId);
        insertSoftDeletedMember(softDeletedMemberId);
        try {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                try {
                    job.run(new DefaultApplicationArguments(
                            "--maintenance.action=validate", "--maintenance.file=" + input
                    ));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .rootCause()
                    .hasMessage("member-info-import IDENTIFIER_COLLISION recordIndex=0 field=id");

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM member_information_import_batches WHERE id = ?", Long.class, batchId))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM members WHERE import_batch_id = ?", Long.class, batchId))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM annual_member_information_responses WHERE import_batch_id = ?",
                    Long.class, batchId))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM activity_logs WHERE target_id = ? AND action = 'MEMBER_INFORMATION_IMPORTED'",
                    Long.class, batchId))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM members WHERE id = ? AND deleted_at IS NOT NULL",
                    Long.class, softDeletedMemberId))
                    .isEqualTo(1L);
        } finally {
            deleteMember(softDeletedMemberId);
            cleanupImport(batchId);
        }
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-014 - soft-deleted extra imported row -> idempotent rerun fails closed without mutation")
    void softDeletedExtraImportedRowShouldRejectIdempotentRerunWithoutMutation(@TempDir Path directory)
            throws Exception {
        ObjectNode document = MemberInformationImportJobTest.syntheticApprovedDocument(mapper, 74);
        UUID batchId = UUID.fromString(document.path("batch").path("id").asText());
        ((ObjectNode) document.path("batch")).put(
                "datasetChecksum", MemberInformationImportJobTest.checksum(mapper, document));
        Path input = directory.resolve("synthetic-soft-deleted-extra-imported-row.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        when(context.getBeansOfType(ExitCodeGenerator.class)).thenReturn(Map.of());
        MemberInformationImportJob job = new MemberInformationImportJob(
                mapper, batches, members, responses, activityEvents, context);
        UUID extraMemberId = UUID.fromString("01970000-0000-7000-8000-000000000098");

        cleanupImport(batchId);
        deleteMember(extraMemberId);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                try {
                    job.run(new DefaultApplicationArguments(
                            "--maintenance.action=apply", "--maintenance.file=" + input,
                            "--maintenance.actor-reference=synthetic-extra-row-operator",
                            "--maintenance.reason=Synthetic extra-row first apply"
                    ));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            insertSoftDeletedMember(extraMemberId, batchId);
            Timestamp executedAtBefore = jdbcTemplate.queryForObject(
                    "SELECT executed_at FROM member_information_import_batches WHERE id = ?",
                    Timestamp.class, batchId);

            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
                try {
                    job.run(new DefaultApplicationArguments(
                            "--maintenance.action=apply", "--maintenance.file=" + input,
                            "--maintenance.actor-reference=synthetic-extra-row-operator",
                            "--maintenance.reason=Synthetic extra-row rerun"
                    ));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }))
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .rootCause()
                    .hasMessage("member-info-import PARTIAL_OR_CORRUPTED_BATCH recordIndex=-1 field=batch.id");

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT executed_at FROM member_information_import_batches WHERE id = ?",
                    Timestamp.class, batchId)).isEqualTo(executedAtBefore);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM members WHERE import_batch_id = ?", Long.class, batchId))
                    .isEqualTo(75L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM annual_member_information_responses WHERE import_batch_id = ?",
                    Long.class, batchId)).isEqualTo(74L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM activity_logs WHERE target_id = ? AND action = 'MEMBER_INFORMATION_IMPORTED'",
                    Long.class, batchId)).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM members WHERE id = ? AND deleted_at IS NOT NULL",
                    Long.class, extraMemberId)).isEqualTo(1L);
        } finally {
            deleteMember(extraMemberId);
            cleanupImport(batchId);
        }
    }

    private JsonNode readJson(Object value) {
        try {
            return mapper.readTree(String.valueOf(value));
        } catch (Exception exception) {
            throw new AssertionError("Synthetic activity metadata must be readable JSON.", exception);
        }
    }

    private void cleanupImport(UUID batchId) {
        jdbcTemplate.update("DELETE FROM activity_logs WHERE target_id = ?", batchId);
        jdbcTemplate.update("DELETE FROM annual_member_occupations WHERE response_id IN "
                + "(SELECT id FROM annual_member_information_responses WHERE import_batch_id = ?)", batchId);
        jdbcTemplate.update("DELETE FROM annual_member_information_responses WHERE import_batch_id = ?", batchId);
        jdbcTemplate.update("DELETE FROM member_experiences WHERE member_id IN "
                + "(SELECT id FROM members WHERE import_batch_id = ?)", batchId);
        jdbcTemplate.update("DELETE FROM member_sacraments WHERE member_id IN "
                + "(SELECT id FROM members WHERE import_batch_id = ?)", batchId);
        jdbcTemplate.update("DELETE FROM member_contribution_areas WHERE member_id IN "
                + "(SELECT id FROM members WHERE import_batch_id = ?)", batchId);
        jdbcTemplate.update("DELETE FROM member_other_contribution_areas WHERE member_id IN "
                + "(SELECT id FROM members WHERE import_batch_id = ?)", batchId);
        jdbcTemplate.update("DELETE FROM members WHERE import_batch_id = ?", batchId);
        jdbcTemplate.update("DELETE FROM member_information_import_batches WHERE id = ?", batchId);
    }

    private void insertSoftDeletedMember(UUID memberId) {
        insertSoftDeletedMember(memberId, null);
    }

    private void insertSoftDeletedMember(UUID memberId, UUID importBatchId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO members (id, account_id, version, first_name, surname, birth_date, gam_entry_date, "
                        + "residential_city, phone_number, contact_email, dietary_restriction_status, status, "
                        + "import_batch_id, created_at, updated_at, deleted_at) VALUES (?, NULL, 0, 'Synthetic', 'Retained', "
                        + "DATE '1990-01-01', DATE '2020-01-01', 'Synthetic City', '+5519998877665', ?, "
                        + "CAST('NOT_INFORMED' AS member_information_status_enum), "
                        + "CAST('ACTIVE' AS member_status_enum), ?, ?, ?, ?)",
                memberId, "soft-deleted-" + memberId + "@example.com", importBatchId, now, now, now);
    }

    private void deleteMember(UUID memberId) {
        jdbcTemplate.update("DELETE FROM members WHERE id = ?", memberId);
    }

    private void insertImportActivity(UUID batchId, Timestamp occurredAt) {
        jdbcTemplate.update("INSERT INTO activity_logs (id, actor_kind, actor_reference, target_type, target_id, "
                        + "action, reason, metadata, occurred_at) VALUES (?, 'DEVELOPER', 'synthetic-integrity-operator', "
                        + "'MEMBER_INFORMATION_IMPORT_BATCH', ?, 'MEMBER_INFORMATION_IMPORTED', "
                        + "'Synthetic integrity import', CAST(? AS jsonb), ?)",
                UUIDGenerator.generateUUIDV7(), batchId,
                "{\"surveyCycle\":2026,\"memberCount\":1,\"responseCount\":1}", occurredAt);
    }
}
