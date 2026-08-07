package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.persistence.AnnualMemberInformationResponseRepository;
import br.org.gam.api.member.persistence.AnnualMemberInformationResponseEntity;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberInformationImportBatchEntity;
import br.org.gam.api.member.persistence.MemberInformationImportBatchRepository;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

@UnitTest
@FunctionalTest
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("Functional - Member information import job")
class MemberInformationImportJobTest {

    @Mock MemberInformationImportBatchRepository batches;
    @Mock MemberRepository members;
    @Mock AnnualMemberInformationResponseRepository responses;
    @Mock ActivityEvents activities;
    @Mock ConfigurableApplicationContext context;

    @ParameterizedTest(name = "recordCount={0}")
    @ValueSource(ints = {73, 75})
    @DisplayName("REQ-MEMBER-IMPORT-008/012 - only the reviewed 74-record dataset cardinality is accepted")
    void validationShouldRejectEveryNonSeventyFourDataset(int recordCount, @TempDir Path directory) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, recordCount);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum(mapper, document));
        Path input = directory.resolve("synthetic-invalid-cardinality-" + recordCount + ".json");
        Files.writeString(input, mapper.writeValueAsString(document));

        assertThatThrownBy(() -> new MemberInformationImportJob(
                mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=validate", "--maintenance.file=" + input
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("member-info-import")
                .hasMessageContaining("recordIndex=-1")
                .hasMessageContaining("field=records");

        verifyNoInteractions(batches, activities);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidStrictProjectionCases")
    @DisplayName("REQ-MEMBER-IMPORT-008/012 - non-canonical cycle, status, and custom contributions are rejected")
    void validationShouldRejectNonCanonicalReviewedProjection(
            String scenario,
            Consumer<ObjectNode> mutation,
            String expectedDiagnostic,
            @TempDir Path directory
    ) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        mutation.accept(document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum(mapper, document));
        Path input = directory.resolve("synthetic-strict-projection-" + scenario + ".json");
        Files.writeString(input, mapper.writeValueAsString(document));

        assertThatThrownBy(() -> new MemberInformationImportJob(
                mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=validate", "--maintenance.file=" + input
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("member-info-import")
                .hasMessageContaining(expectedDiagnostic);

        verifyNoInteractions(batches, activities);
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-014/015 - idempotent 74-record rerun requires exact names, responses, and one import activity")
    void idempotentRerunShouldRejectNullNamesResponseExistenceFallbackAndMissingActivity(
            @TempDir Path directory
    ) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        String checksum = checksum(mapper, document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum);
        Path input = directory.resolve("synthetic-corrupted-74-rerun.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        UUID batchId = UUID.fromString(document.path("batch").path("id").asText());
        Map<UUID, MemberEntity> corruptedMembers = new java.util.HashMap<>();
        document.path("records").forEach(record -> {
            UUID memberId = UUID.fromString(record.path("member").path("id").asText());
            MemberEntity corrupted = new MemberEntity();
            corrupted.setId(memberId);
            corrupted.setImportBatchId(batchId);
            corruptedMembers.put(memberId, corrupted);
        });
        when(batches.findById(batchId)).thenReturn(Optional.of(new MemberInformationImportBatchEntity(
                batchId, 2026, checksum, 74, 74, java.time.Instant.parse("2026-01-01T00:00:00Z"),
                "Synthetic original reason")));
        when(members.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(corruptedMembers.get(invocation.getArgument(0))));
        when(responses.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(responses.existsById(any(UUID.class))).thenReturn(true);
        when(members.findAll()).thenReturn(List.copyOf(corruptedMembers.values()));
        when(responses.findAll()).thenReturn(List.of());
        when(members.countImportActivities(batchId)).thenReturn(0L);

        assertThatThrownBy(() -> new MemberInformationImportJob(
                mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=apply", "--maintenance.file=" + input,
                        "--maintenance.actor-reference=synthetic-rerun-operator",
                        "--maintenance.reason=Synthetic exact rerun"
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member-info-import PARTIAL_OR_CORRUPTED_BATCH recordIndex=-1 field=batch.id");
        verifyNoInteractions(activities);
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-011/012 - successful synthetic validation -> no writes and only safe operational data in logs")
    void validationShouldNotWriteOrLogPathOrPersonalData(@TempDir Path directory, CapturedOutput output) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        String checksum = checksum(mapper, document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum);
        Path input = directory.resolve("synthetic-approved.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        when(context.getBeansOfType(ExitCodeGenerator.class)).thenReturn(Map.of());

        new MemberInformationImportJob(mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=validate",
                        "--maintenance.file=" + input
                ));

        verifyNoInteractions(batches, activities);
        assertThat(output.getAll())
                .contains("Member information import validate succeeded")
                .contains("01970000-0000-7000-8000-000000000001")
                .contains("recordCount 74")
                .doesNotContain(input.toString(), input.getFileName().toString())
                .doesNotContain("Ana", "Silva", "1990-01-01", "Synthetic City",
                        "ana.fixture@example.com", "+5519998877665");
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-012/013 - unsupported contribution -> safe field diagnostic and no writes")
    void invalidContributionShouldFailEntireValidationWithoutProtectedDiagnostics(
            @TempDir Path directory, CapturedOutput output) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        ((ObjectNode) document.path("records").path(0).path("member"))
                .putArray("contributionAreas").add("UNSUPPORTED_SYNTHETIC_CONTRIBUTION");
        Path input = directory.resolve("synthetic-invalid-contribution.json");
        Files.writeString(input, mapper.writeValueAsString(document));

        assertThatThrownBy(() -> new MemberInformationImportJob(
                mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=validate",
                        "--maintenance.file=" + input
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("member-info-import")
                .hasMessageContaining("recordIndex=0")
                .hasMessageContaining("field=member.contributionAreas")
                .satisfies(failure -> assertThat(failure.getMessage())
                        .doesNotContain(input.toString(), input.getFileName().toString())
                        .doesNotContain("Ana", "Silva", "1990-01-01", "Synthetic City",
                                "ana.fixture@example.com", "+5519998877665"));

        verify(batches, never()).save(org.mockito.ArgumentMatchers.any());
        verify(members, never()).save(org.mockito.ArgumentMatchers.any());
        verify(members, never()).flush();
        verify(responses, never()).save(org.mockito.ArgumentMatchers.any());
        verify(responses, never()).flush();
        verifyNoInteractions(activities);
        assertThat(output.getAll())
                .doesNotContain(input.toString(), input.getFileName().toString())
                .doesNotContain("Ana", "Silva", "1990-01-01", "Synthetic City",
                        "ana.fixture@example.com", "+5519998877665");
    }

    @ParameterizedTest(name = "{0}-{1}")
    @MethodSource("nonTextualFieldCases")
    @DisplayName("REQ-MEMBER-IMPORT-012/013 - textual fields reject every non-text JSON node without writes")
    void textualFieldsShouldRejectNonTextJsonNodesWithoutCoercion(
            String requiredness,
            String nodeType,
            @TempDir Path directory
    ) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        boolean requiredText = "required".equals(requiredness);
        ObjectNode parent = requiredText
                ? (ObjectNode) document.path("records").path(0).path("member")
                : (ObjectNode) document.path("records").path(0).path("annualResponse");
        String field = requiredText ? "residentialCity" : "additionalComments";
        String diagnosticField = requiredText
                ? "member.residentialCity"
                : "annualResponse.additionalComments";
        putNonTextNode(parent, field, nodeType);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum(mapper, document));
        Path input = directory.resolve("synthetic-non-text-" + requiredness + "-" + nodeType + ".json");
        Files.writeString(input, mapper.writeValueAsString(document));

        assertThatThrownBy(() -> new MemberInformationImportJob(
                mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=validate",
                        "--maintenance.file=" + input
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("member-info-import")
                .hasMessageContaining("recordIndex=0")
                .hasMessageContaining("field=" + diagnosticField)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .doesNotContain(input.toString(), input.getFileName().toString())
                        .doesNotContain("Ana", "Silva", "1990-01-01", "Synthetic City",
                                "ana.fixture@example.com", "+5519998877665"));

        verify(batches, never()).save(any());
        verify(members, never()).save(any());
        verify(members, never()).flush();
        verify(responses, never()).save(any());
        verify(responses, never()).flush();
        verifyNoInteractions(activities);
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-013/014 - existing canonical full name -> safe review collision and no writes")
    void existingCanonicalNameShouldFailValidationWithoutAdoptingTheMember(@TempDir Path directory) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        String checksum = checksum(mapper, document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum);
        Path input = directory.resolve("synthetic-name-collision.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        JsonNode firstMember = document.path("records").path(0).path("member");
        UUID importedMemberId = UUID.fromString(firstMember.path("id").asText());
        String canonicalName = firstMember.path("firstName").asText() + " " + firstMember.path("surname").asText();
        when(members.existsDifferentByCanonicalFullName(canonicalName, importedMemberId)).thenReturn(true);

        assertThatThrownBy(() -> new MemberInformationImportJob(
                mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=validate",
                        "--maintenance.file=" + input
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member-info-import EXISTING_NAME_REVIEW_COLLISION recordIndex=0 field=member.name")
                .satisfies(failure -> assertThat(failure.getMessage())
                        .doesNotContain(input.toString(), "Ana", "Silva", "ana.fixture@example.com"));

        verify(batches, never()).save(org.mockito.ArgumentMatchers.any());
        verify(members, never()).save(org.mockito.ArgumentMatchers.any());
        verify(members, never()).flush();
        verify(responses, never()).save(org.mockito.ArgumentMatchers.any());
        verify(responses, never()).flush();
        verifyNoInteractions(activities);
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-008/009/013 - exact synthetic 2026 projection -> 74 active Account-less records with complete provenance")
    void exactSyntheticDatasetShouldApplyAllSeventyFourCompleteRecords(@TempDir Path directory) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        String checksum = checksum(mapper, document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum);
        Path input = directory.resolve("synthetic-approved-74.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        when(context.getBeansOfType(ExitCodeGenerator.class)).thenReturn(Map.of());

        runWithSyntheticTransactionCompletion(() -> new MemberInformationImportJob(
                mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=apply",
                        "--maintenance.file=" + input,
                        "--maintenance.actor-reference=synthetic-74-operator",
                        "--maintenance.reason=Synthetic 74-record import"
                )));

        ArgumentCaptor<MemberInformationImportBatchEntity> batch =
                ArgumentCaptor.forClass(MemberInformationImportBatchEntity.class);
        ArgumentCaptor<MemberEntity> importedMembers = ArgumentCaptor.forClass(MemberEntity.class);
        ArgumentCaptor<AnnualMemberInformationResponseEntity> importedResponses =
                ArgumentCaptor.forClass(AnnualMemberInformationResponseEntity.class);
        verify(batches).save(batch.capture());
        verify(members, times(74)).save(importedMembers.capture());
        verify(responses, times(74)).save(importedResponses.capture());
        assertThat(batch.getValue().getSurveyCycle()).isEqualTo(2026);
        assertThat(batch.getValue().getImportedMemberCount()).isEqualTo(74);
        assertThat(batch.getValue().getImportedResponseCount()).isEqualTo(74);
        assertThat(importedMembers.getAllValues())
                .hasSize(74)
                .allSatisfy(member -> {
                    assertThat(member.getStatus().name()).isEqualTo("ACTIVE");
                    assertThat(member.getAccount()).isNull();
                    assertThat(member.getImportBatchId()).isEqualTo(batch.getValue().getId());
                    assertThat(member.getOtherContributionAreas()).isEmpty();
                    assertThat(member.getContributionAreas()).hasSize(1);
                });
        assertThat(importedResponses.getAllValues())
                .hasSize(74)
                .allSatisfy(response -> {
                    assertThat(response.getSurveyCycle()).isEqualTo(2026);
                    assertThat(response.getImportBatchId()).isEqualTo(batch.getValue().getId());
                    assertThat(response.getMember()).isIn(importedMembers.getAllValues());
                });
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-014 and REQ-MEMBER-IMPORT-012 - imported annual text -> Unicode trim, NFC, blank-to-null, and code-point boundary")
    void importedAnnualTextShouldApplyUnicodeNormalizationAndBoundaries(@TempDir Path directory) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        ObjectNode annual = (ObjectNode) document.path("records").path(0).path("annualResponse");
        annual.put("formationAndMeetingInterests", "\u00a0Forma\u0063\u0327\u00e3o sint\u00e9tica\u00a0");
        annual.put("additionalComments", "\u00a0\u2003\u00a0");
        annual.put("oratorioActivitySuggestions", "\ud83c\udf06".repeat(2_000));
        String checksum = checksum(mapper, document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum);
        Path input = directory.resolve("synthetic-unicode-annual.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        when(context.getBeansOfType(ExitCodeGenerator.class)).thenReturn(Map.of());

        runWithSyntheticTransactionCompletion(() -> new MemberInformationImportJob(
                mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=apply", "--maintenance.file=" + input,
                        "--maintenance.actor-reference=synthetic-unicode-operator",
                        "--maintenance.reason=Synthetic Unicode import"
                )));

        ArgumentCaptor<AnnualMemberInformationResponseEntity> captured =
                ArgumentCaptor.forClass(AnnualMemberInformationResponseEntity.class);
        verify(responses, times(74)).save(captured.capture());
        AnnualMemberInformationResponseEntity first = captured.getAllValues().getFirst();
        assertThat(first.getFormationAndMeetingInterests()).isEqualTo("Formação sintética");
        assertThat(first.getAdditionalComments()).isNull();
        assertThat(first.getOratorioActivitySuggestions()).hasSize(4_000);
        assertThat(first.getOratorioActivitySuggestions().codePointCount(0, 4_000)).isEqualTo(2_000);
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-014 - complete expected projection plus extra imported row -> corrupted batch, not no-op")
    void idempotentRerunShouldRejectExtraImportedRow(@TempDir Path directory) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        String checksum = checksum(mapper, document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum);
        Path input = directory.resolve("synthetic-extra-row-corruption.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        AppliedProjection applied = applyAndStubCompleteProjection(mapper, document);
        MemberEntity extra = new MemberEntity();
        extra.setId(UUID.fromString("01970000-0000-7000-8000-000000000099"));
        extra.setImportBatchId(applied.batch().getId());
        List<MemberEntity> importedPlusExtra = new ArrayList<>(applied.members().values());
        importedPlusExtra.add(extra);
        when(members.findAll()).thenReturn(importedPlusExtra);

        assertThatThrownBy(() -> new MemberInformationImportJob(
                mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=apply", "--maintenance.file=" + input,
                        "--maintenance.actor-reference=synthetic-rerun-operator",
                        "--maintenance.reason=Synthetic rerun reason"
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member-info-import PARTIAL_OR_CORRUPTED_BATCH recordIndex=-1 field=batch.id");
        verifyNoInteractions(activities);
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-013/015 - first apply -> batch, Account-less records, and one minimized activity")
    void firstApplyShouldPersistCompleteSyntheticBatchAndOneMinimizedActivity(@TempDir Path directory) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        String checksum = checksum(mapper, document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum);
        Path input = directory.resolve("synthetic-first-apply.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        when(context.getBeansOfType(ExitCodeGenerator.class)).thenReturn(Map.of());

        runWithSyntheticTransactionCompletion(() ->
                new MemberInformationImportJob(mapper, batches, members, responses, activities, context)
                        .run(new DefaultApplicationArguments(
                                "--maintenance.action=apply",
                                "--maintenance.file=" + input,
                                "--maintenance.actor-reference=synthetic-test-operator",
                                "--maintenance.reason=  Synthetic maintenance reason  "
                        )));

        ArgumentCaptor<MemberInformationImportBatchEntity> batch =
                ArgumentCaptor.forClass(MemberInformationImportBatchEntity.class);
        ArgumentCaptor<MemberEntity> member = ArgumentCaptor.forClass(MemberEntity.class);
        ArgumentCaptor<AnnualMemberInformationResponseEntity> response =
                ArgumentCaptor.forClass(AnnualMemberInformationResponseEntity.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(batches).save(batch.capture());
        verify(members, times(74)).save(member.capture());
        verify(members).flush();
        verify(responses, times(74)).save(response.capture());
        verify(responses).flush();
        verify(activities).developerMaintenance(
                eq(ActivityAction.MEMBER_INFORMATION_IMPORTED),
                eq(UUID.fromString("01970000-0000-7000-8000-000000000001")),
                eq("member_information_import_batches"),
                eq("Synthetic maintenance reason"), isNull(), metadata.capture());

        assertThat(batch.getValue())
                .returns(UUID.fromString("01970000-0000-7000-8000-000000000001"), MemberInformationImportBatchEntity::getId)
                .returns(2026, MemberInformationImportBatchEntity::getSurveyCycle)
                .returns(checksum, MemberInformationImportBatchEntity::getDatasetChecksum)
                .returns(74, MemberInformationImportBatchEntity::getImportedMemberCount)
                .returns(74, MemberInformationImportBatchEntity::getImportedResponseCount)
                .returns("Synthetic maintenance reason", MemberInformationImportBatchEntity::getReason);
        assertThat(batch.getValue().getExecutedAt()).isNotNull();
        assertThat(member.getAllValues()).hasSize(74).allSatisfy(value -> {
            assertThat(value.getAccount()).isNull();
            assertThat(value.getImportBatchId()).isEqualTo(batch.getValue().getId());
        });
        assertThat(response.getAllValues()).hasSize(74).allSatisfy(value -> {
            assertThat(value.getMember()).isIn(member.getAllValues());
            assertThat(value.getImportBatchId()).isEqualTo(batch.getValue().getId());
        });
        assertThat(metadata.getValue()).containsExactlyInAnyOrderEntriesOf(
                Map.of("surveyCycle", 2026, "memberCount", 74, "responseCount", 74));
        assertThat(metadata.getValue().toString())
                .doesNotContain(checksum, input.toString(), "Ana", "Silva",
                        "ana.fixture@example.com", "+5519998877665");
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-014/015 - complete applied batch -> successful no-op without writes or activity")
    void completeAppliedBatchShouldBeIdempotentNoOp(@TempDir Path directory, CapturedOutput output) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        String checksum = checksum(mapper, document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum);
        Path input = directory.resolve("synthetic-idempotent-rerun.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        applyAndStubCompleteProjection(mapper, document);

        new MemberInformationImportJob(mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=apply",
                        "--maintenance.file=" + input,
                        "--maintenance.actor-reference=synthetic-rerun-operator",
                        "--maintenance.reason=Different synthetic rerun reason"
                ));

        verify(batches, never()).save(org.mockito.ArgumentMatchers.any());
        verify(members, never()).save(org.mockito.ArgumentMatchers.any());
        verify(members, never()).flush();
        verify(responses, never()).save(org.mockito.ArgumentMatchers.any());
        verify(responses, never()).flush();
        verifyNoInteractions(activities);
        assertThat(output.getAll()).contains("Member information import apply succeeded");
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-014/015 - incomplete applied batch -> fail closed without repair or activity")
    void incompleteAppliedBatchShouldFailWithoutRepair(@TempDir Path directory) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = syntheticApprovedDocument(mapper, 74);
        String checksum = checksum(mapper, document);
        ((ObjectNode) document.path("batch")).put("datasetChecksum", checksum);
        Path input = directory.resolve("synthetic-corrupted-rerun.json");
        Files.writeString(input, mapper.writeValueAsString(document));
        AppliedProjection applied = applyAndStubCompleteProjection(mapper, document);
        UUID missingResponseId = applied.responses().keySet().iterator().next();
        Map<UUID, AnnualMemberInformationResponseEntity> incompleteResponses =
                new java.util.LinkedHashMap<>(applied.responses());
        incompleteResponses.remove(missingResponseId);
        reset(responses);
        when(responses.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(incompleteResponses.get(invocation.getArgument(0))));
        when(responses.existsById(any(UUID.class))).thenAnswer(invocation ->
                incompleteResponses.containsKey(invocation.getArgument(0)));
        when(responses.findAll()).thenReturn(List.copyOf(incompleteResponses.values()));

        assertThatThrownBy(() -> new MemberInformationImportJob(
                mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=apply",
                        "--maintenance.file=" + input,
                        "--maintenance.actor-reference=synthetic-rerun-operator",
                        "--maintenance.reason=Synthetic rerun reason"
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member-info-import PARTIAL_OR_CORRUPTED_BATCH recordIndex=-1 field=batch.id");

        verify(batches, never()).save(org.mockito.ArgumentMatchers.any());
        verify(members, never()).save(org.mockito.ArgumentMatchers.any());
        verify(members, never()).flush();
        verify(responses, never()).save(org.mockito.ArgumentMatchers.any());
        verify(responses, never()).flush();
        verifyNoInteractions(activities);
    }

    static ObjectNode syntheticApprovedDocument(ObjectMapper mapper) {
        ObjectNode document = mapper.createObjectNode();
        document.put("schemaVersion", "gam-member-information-import/v1");
        document.put("documentStatus", "APPROVED");
        ObjectNode batch = document.putObject("batch");
        batch.put("id", "01970000-0000-7000-8000-000000000001");
        batch.put("surveyCycle", 2026);
        batch.put("datasetChecksum", "sha256:" + "0".repeat(64));
        ObjectNode record = document.putArray("records").addObject();
        record.put("reviewStatus", "APPROVED");
        record.putArray("reviewIssues");
        record.put("sourceReference", "CSV_ROW_1");
        ObjectNode member = record.putObject("member");
        member.put("id", "01970000-0000-7000-8000-000000000002");
        member.put("firstName", "Ana"); member.put("surname", "Silva");
        member.put("birthDate", "1990-01-01"); member.put("gamEntryDate", "2020-01-01");
        member.put("residentialCity", "Synthetic City"); member.put("phoneNumber", "+5519998877665");
        member.put("contactEmail", "ana.fixture@example.com"); member.put("status", "ACTIVE");
        member.putObject("dietaryRestriction").put("status", "NOT_INFORMED").putNull("details");
        ObjectNode experiences = member.putObject("experiences");
        for (String value : List.of("JORNADA_MISSIONARIA", "CURSO_DE_LIDERANCA", "PASCOA_JUVENIL", "ACAMPABOSCO")) experiences.put(value, "NOT_INFORMED");
        ObjectNode sacraments = member.putObject("sacraments");
        for (String value : List.of("BATISMO", "PRIMEIRA_COMUNHAO", "CRISMA")) sacraments.put(value, "NOT_INFORMED");
        member.putArray("contributionAreas"); member.putArray("otherContributionAreas");
        ObjectNode annual = record.putObject("annualResponse");
        annual.put("id", "01970000-0000-7000-8000-000000000003");
        annual.put("memberId", member.path("id").asText()); annual.put("surveyCycle", 2026); annual.putNull("submittedAt");
        annual.putObject("occupations").putArray("values").add("WORK");
        ((ObjectNode) annual.path("occupations")).putNull("details");
        annual.putObject("healthCondition").put("status", "NO").putNull("details");
        annual.put("religiousVocationConsidered", "NO"); annual.put("massAttendanceFrequency", "WEEKLY");
        annual.putObject("saturdayOratorioImpediment").put("status", "NO").putNull("details");
        annual.putNull("formationAndMeetingInterests"); annual.put("coordinationInterest", "NO");
        annual.putNull("additionalComments"); annual.putNull("oratorioActivitySuggestions"); annual.putNull("instagramPostSuggestions");
        return document;
    }

    static ObjectNode syntheticApprovedDocument(ObjectMapper mapper, int recordCount) {
        ObjectNode document = syntheticApprovedDocument(mapper);
        ObjectNode template = ((ObjectNode) document.path("records").path(0)).deepCopy();
        ArrayNode records = document.putArray("records");
        List<String> fixedCatalog = List.of(
                "GAME_REFEREE", "CRAFTS", "MUSIC", "PRAYER_LEADERSHIP", "BOA_TARDE_STORYTELLING",
                "DANCE", "BALLOON_SCULPTURE", "FOOTBALL", "VOLLEYBALL", "BASKETBALL", "HANDBALL",
                "PHOTOGRAPHY_AND_VIDEO", "PUBLIC_READING", "FACE_PAINTING", "FIRST_AID",
                "GINCANA_LEADERSHIP", "TECHNOLOGY", "TERERE"
        );
        for (int index = 0; index < recordCount; index++) {
            ObjectNode record = template.deepCopy();
            String memberId = String.format("01970000-0000-7000-8000-%012d", 100 + index * 2);
            String responseId = String.format("01970000-0000-7000-8000-%012d", 101 + index * 2);
            ObjectNode member = (ObjectNode) record.path("member");
            member.put("id", memberId);
            member.put("surname", "Synthetic " + alphabeticCode(index));
            member.put("contactEmail", "synthetic-member-" + (index + 1) + "@example.com");
            member.putArray("contributionAreas").add(fixedCatalog.get(index % fixedCatalog.size()));
            member.putArray("otherContributionAreas");
            ObjectNode annual = (ObjectNode) record.path("annualResponse");
            annual.put("id", responseId);
            annual.put("memberId", memberId);
            record.put("sourceReference", "CSV_ROW_" + (index + 1));
            records.add(record);
        }
        return document;
    }

    private static String alphabeticCode(int index) {
        int first = index / 26;
        int second = index % 26;
        return Character.toString('A' + first) + Character.toString('a' + second);
    }

    private static Stream<Arguments> invalidStrictProjectionCases() {
        return Stream.of(
                Arguments.of("cycle", (Consumer<ObjectNode>) document -> {
                    ((ObjectNode) document.path("batch")).put("surveyCycle", 2025);
                    document.path("records").forEach(record ->
                            ((ObjectNode) record.path("annualResponse")).put("surveyCycle", 2025));
                }, "recordIndex=-1 field=batch.surveyCycle"),
                Arguments.of("inactive-member", (Consumer<ObjectNode>) document ->
                                ((ObjectNode) document.path("records").path(0).path("member"))
                                        .put("status", "INACTIVE"),
                        "recordIndex=0 field=member.status"),
                Arguments.of("custom-contribution", (Consumer<ObjectNode>) document ->
                                ((ObjectNode) document.path("records").path(0).path("member"))
                                        .putArray("otherContributionAreas").add("Synthetic custom contribution"),
                        "recordIndex=0 field=member.otherContributionAreas")
        );
    }

    private static Stream<Arguments> nonTextualFieldCases() {
        return Stream.of("number", "boolean", "object", "array")
                .flatMap(nodeType -> Stream.of(
                        Arguments.of("required", nodeType),
                        Arguments.of("optional", nodeType)
                ));
    }

    private static void putNonTextNode(ObjectNode parent, String field, String nodeType) {
        switch (nodeType) {
            case "number" -> parent.put(field, 42);
            case "boolean" -> parent.put(field, true);
            case "object" -> parent.putObject(field).put("synthetic", "value");
            case "array" -> parent.putArray(field).add("synthetic");
            default -> throw new IllegalArgumentException("Unsupported synthetic node type " + nodeType);
        }
    }

    private AppliedProjection applyAndStubCompleteProjection(ObjectMapper mapper, ObjectNode document) throws Exception {
        when(context.getBeansOfType(ExitCodeGenerator.class)).thenReturn(Map.of());
        runWithSyntheticTransactionCompletion(() -> new MemberInformationImportJob(
                mapper, batches, members, responses, activities, context)
                .run(new DefaultApplicationArguments(
                        "--maintenance.action=apply",
                        "--maintenance.file=" + writeSyntheticDocument(mapper, document),
                        "--maintenance.actor-reference=synthetic-projection-operator",
                        "--maintenance.reason=Synthetic projection setup"
                )));

        ArgumentCaptor<MemberInformationImportBatchEntity> batch =
                ArgumentCaptor.forClass(MemberInformationImportBatchEntity.class);
        ArgumentCaptor<MemberEntity> importedMembers = ArgumentCaptor.forClass(MemberEntity.class);
        ArgumentCaptor<AnnualMemberInformationResponseEntity> importedResponses =
                ArgumentCaptor.forClass(AnnualMemberInformationResponseEntity.class);
        verify(batches).save(batch.capture());
        verify(members, times(74)).save(importedMembers.capture());
        verify(responses, times(74)).save(importedResponses.capture());
        Map<UUID, MemberEntity> memberProjection = importedMembers.getAllValues().stream()
                .collect(java.util.stream.Collectors.toMap(MemberEntity::getId, value -> value));
        Map<UUID, AnnualMemberInformationResponseEntity> responseProjection = importedResponses.getAllValues().stream()
                .collect(java.util.stream.Collectors.toMap(AnnualMemberInformationResponseEntity::getId, value -> value));

        clearInvocations(batches, members, responses, activities, context);
        when(batches.findById(batch.getValue().getId())).thenReturn(Optional.of(batch.getValue()));
        when(members.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(memberProjection.get(invocation.getArgument(0))));
        when(responses.findById(any(UUID.class))).thenAnswer(invocation ->
                Optional.ofNullable(responseProjection.get(invocation.getArgument(0))));
        when(responses.existsById(any(UUID.class))).thenAnswer(invocation ->
                responseProjection.containsKey(invocation.getArgument(0)));
        when(members.findAll()).thenReturn(List.copyOf(memberProjection.values()));
        when(responses.findAll()).thenReturn(List.copyOf(responseProjection.values()));
        when(members.countImportActivities(batch.getValue().getId())).thenReturn(1L);
        return new AppliedProjection(batch.getValue(), memberProjection, responseProjection);
    }

    private Path writeSyntheticDocument(ObjectMapper mapper, ObjectNode document) throws Exception {
        Path path = Files.createTempFile("synthetic-complete-projection-", ".json");
        Files.writeString(path, mapper.writeValueAsString(document));
        path.toFile().deleteOnExit();
        return path;
    }

    private record AppliedProjection(
            MemberInformationImportBatchEntity batch,
            Map<UUID, MemberEntity> members,
            Map<UUID, AnnualMemberInformationResponseEntity> responses
    ) {}

    static String checksum(ObjectMapper mapper, ObjectNode document) throws Exception {
        ObjectNode canonical = mapper.createObjectNode();
        canonical.put("schemaVersion", document.path("schemaVersion").asText());
        ObjectNode batch = canonical.putObject("batch");
        batch.put("id", document.path("batch").path("id").asText());
        batch.put("surveyCycle", document.path("batch").path("surveyCycle").asInt());
        ArrayNode payloads = canonical.putArray("records");
        List<JsonNode> records = new ArrayList<>();
        document.path("records").forEach(records::add);
        records.stream().sorted(java.util.Comparator.comparing(record -> record.path("member").path("id").asText()))
                .forEach(record -> {
                    ObjectNode payload = payloads.addObject();
                    payload.set("annualResponse", sorted(mapper, record.path("annualResponse")));
                    payload.set("member", sorted(mapper, record.path("member")));
                });
        byte[] bytes = mapper.writeValueAsBytes(sorted(mapper, canonical));
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static JsonNode sorted(ObjectMapper mapper, JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            List<String> names = new ArrayList<>(); value.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> result.set(name, sorted(mapper, value.get(name))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode(); value.forEach(item -> result.add(sorted(mapper, item))); return result;
        }
        return value;
    }

    private static void runWithSyntheticTransactionCompletion(ThrowingRunnable action) throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
        } finally {
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
