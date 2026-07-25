package br.org.gam.api.api;

import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@ApiTest
@FunctionalTest
@IntegrationTest
@PersistenceTest
@SecurityTest
@DisplayName("API - Oratoriano additional forms")
class OratorianoAdditionalFormsApiIT extends OratorioModuleApiTestSupport {

    private static final int MEBIBYTE = 1_024 * 1_024;
    private final Map<UUID, UUID> latestSnapshotIds = new LinkedHashMap<>();

    @ParameterizedTest
    @ValueSource(strings = {"PAPER_TRANSCRIPTION", "DIRECT_SYSTEM_ENTRY"})
    @DisplayName("REQ-ORATORIANO-FORM-001 and REQ-ORATORIANO-FORM-002 - accepted origin -> identified DRAFT")
    void acceptedOriginShouldCreateIdentifiedDraft(String origin) {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        clearActivities();

        ExtractableResponse<Response> response = createDraft(caller, oratorianoId, origin);

        assertThat(response.statusCode()).isEqualTo(201);
        UUID formId = UUID.fromString(response.path("id"));
        assertUuidV7(formId);
        assertPublicApiLocation(
                response,
                "/oratorianos/" + oratorianoId + "/forms/" + formId
        );
        assertThat(response.<String>path("oratorianoId")).isEqualTo(oratorianoId.toString());
        assertThat(response.<String>path("status")).isEqualTo("DRAFT");
        assertThat(response.<String>path("origin")).isEqualTo(origin);
        assertThat(response.<String>path("createdBy.id")).isEqualTo(caller.accountId().toString());
        assertThat(response.<String>path("createdAt")).isNotBlank();
        assertThat(activityCountForTarget(formId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-001 - unsupported OTHER origin -> HTTP 400 without draft")
    void unsupportedOriginShouldBeRejected() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Ana", "Souza");
        clearActivities();

        ExtractableResponse<Response> response = createDraft(caller, oratorianoId, "OTHER");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(formCount(oratorianoId)).isZero();
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-003 - empty draft may be saved repeatedly but cannot complete")
    void emptyDraftShouldRemainFlexibleButFailCompletionAtomically() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Paulo", "Mendes");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        clearActivities();

        ExtractableResponse<Response> firstSave = authenticatedJsonRequest(caller)
                .body(Map.of())
                .put(formPath(oratorianoId, formId))
                .then()
                .extract();
        ExtractableResponse<Response> secondSave = authenticatedJsonRequest(caller)
                .body(Map.of())
                .put(formPath(oratorianoId, formId))
                .then()
                .extract();

        assertThat(firstSave.statusCode()).isEqualTo(200);
        assertThat(secondSave.statusCode()).isEqualTo(200);
        assertThat(firstSave.<String>path("status")).isEqualTo("DRAFT");
        assertThat(secondSave.<String>path("status")).isEqualTo("DRAFT");
        assertThat(activityLogCount()).isZero();

        ExtractableResponse<Response> completion = authenticatedJsonRequest(caller)
                .body(Map.of())
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();
        assertThat(completion.statusCode()).isEqualTo(400);
        assertThat(formStatus(formId)).isEqualTo("DRAFT");
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-011 and REQ-ORATORIANO-FORM-012 - print snapshot is immutable and PDF bytes are disposable")
    void printSnapshotShouldRenderDisposableIdentifiedPdf() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Marina", "Sousa");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        clearActivities();

        ExtractableResponse<Response> snapshot = authenticatedJsonRequest(caller)
                .post(formPath(oratorianoId, formId) + "/print-snapshots")
                .then()
                .extract();

        assertThat(snapshot.statusCode()).isEqualTo(201);
        UUID snapshotId = UUID.fromString(snapshot.path("id"));
        assertUuidV7(snapshotId);
        assertThat(snapshot.<String>path("formId")).isEqualTo(formId.toString());
        assertThat(snapshot.<String>path("mode")).containsIgnoringCase("PREFILL");
        assertThat(snapshot.<Number>path("draftRevision").longValue()).isPositive();
        assertThat(snapshot.<Number>path("pageCount").intValue()).isPositive();
        assertThat(snapshot.<String>path("templateVersion")).isNotBlank();
        assertThat(snapshot.<String>path("fingerprint")).hasSize(64);
        assertThat(snapshot.jsonPath().getMap("$")).doesNotContainKeys("pdf", "pdfBytes", "bytes");
        assertThat(activityLogCount()).isEqualTo(1);

        ExtractableResponse<Response> pdf = authenticatedJsonRequest(caller)
                .accept(ContentType.BINARY)
                .get(formPath(oratorianoId, formId) + "/print-snapshots/{snapshotId}/pdf", snapshotId)
                .then()
                .extract();

        assertThat(pdf.statusCode()).isEqualTo(200);
        assertThat(pdf.contentType()).startsWith("application/pdf");
        assertThat(new String(pdf.asByteArray(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(activityLogCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-011 - prefilled PDF -> immutable snapshot data, declarations, signing fields, location, and page identity")
    void prefilledPdfShouldContainTheCompletePrintableSnapshot() throws IOException {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Érik", "García");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        authenticatedJsonRequest(caller)
                .body(completeAccentedAdultFormPayload())
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);

        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        String text = pdfText(snapshot.bytes());

        assertThat(text)
                .contains(
                        oratorianoId.toString(),
                        formId.toString(),
                        snapshot.id().toString(),
                        snapshot.templateVersion(),
                        snapshot.generatedAt().toString(),
                        "Érik",
                        "García",
                        "2000-05-20",
                        "52998224725",
                        "45.678.901-2",
                        "Rua São José",
                        "150 fundos",
                        "Jardim Água Branca",
                        "13400-000",
                        "Limeira",
                        "+5519998877665",
                        "Escola São José",
                        "9º ano",
                        "João",
                        "D'Ávila",
                        "11144477735",
                        "Ana",
                        "Luísa",
                        "12345678909",
                        "Cardiologista Dr. José",
                        "Evitar esforço intenso",
                        "Anti-histamínico",
                        "Tomar após o almoço",
                        "Amendoim e castanhas",
                        "Levar medicação de emergência",
                        "Piracicaba"
                )
                .containsIgnoringCase("PREFILLED");
        assertContainsDeclarationConcepts(text);
        assertThat(countOccurrencesIgnoringCase(text, "rubrica")
                + countOccurrencesIgnoringCase(text, "initial"))
                .as("one handwritten initials field beside each of the six declarations")
                .isGreaterThanOrEqualTo(6);
        assertThat(text.toLowerCase())
                .containsAnyOf("signature", "assinatura");
        assertThat(text)
                .as("fixed signing location must be distinct from the editable address city")
                .containsPattern("(?is)(local|location).{0,40}Piracicaba");

        List<String> pageTexts = pdfPageTexts(snapshot.bytes());
        assertThat(pageTexts).hasSize(snapshot.pageCount());
        for (int page = 0; page < pageTexts.size(); page++) {
            assertThat(pageTexts.get(page))
                    .as("identity and page number on generated page %s", page + 1)
                    .contains(snapshot.id().toString())
                    .containsAnyOf(
                            (page + 1) + " of " + snapshot.pageCount(),
                            (page + 1) + " de " + snapshot.pageCount()
                    );
        }
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-011 - identified blank PDF -> usable complete form matrix without prefilled sensitive values")
    void identifiedBlankPdfShouldContainTheCompleteWritableFormMatrix() throws IOException {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Marina", "Sousa");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));

        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        String text = pdfText(snapshot.bytes());

        assertThat(text)
                .contains(
                        oratorianoId.toString(),
                        formId.toString(),
                        snapshot.id().toString(),
                        snapshot.templateVersion(),
                        snapshot.generatedAt().toString(),
                        "Piracicaba"
                )
                .containsIgnoringCase("BLANK")
                .doesNotContain("52998224725", "Rua São José", "+5519998877665");
        assertContainsFieldConcept(text, "first name", "nome");
        assertContainsFieldConcept(text, "surname", "sobrenome");
        assertContainsFieldConcept(text, "birth date", "data de nascimento");
        assertContainsFieldConcept(text, "cpf");
        assertContainsFieldConcept(text, "rg");
        assertContainsFieldConcept(text, "address", "endereço");
        assertContainsFieldConcept(text, "school", "escola");
        assertContainsFieldConcept(text, "responsible", "responsável");
        assertContainsFieldConcept(text, "father", "pai");
        assertContainsFieldConcept(text, "mother", "mãe");
        assertContainsFieldConcept(text, "health", "saúde");
        assertContainsFieldConcept(text, "at least 18", "18 anos");
        assertContainsFieldConcept(text, "medical follow-up");
        assertContainsFieldConcept(text, "physical activity restriction");
        assertContainsFieldConcept(text, "medicine use");
        assertContainsFieldConcept(text, "allergies");
        assertContainsFieldConcept(text, "convulsions");
        assertContainsFieldConcept(text, "frequent fainting");
        assertContainsFieldConcept(text, "heart condition");
        assertContainsFieldConcept(text, "other health condition");
        assertContainsFieldConcept(text, "important instructions");
        assertContainsFieldConcept(text, "other care");
        assertThat(countOccurrencesIgnoringCase(text, "yes"))
                .as("one YES choice for every structured health question")
                .isGreaterThanOrEqualTo(8);
        assertThat(countOccurrencesIgnoringCase(text, "no"))
                .as("one NO choice for every structured health question")
                .isGreaterThanOrEqualTo(8);
        assertThat(countOccurrencesIgnoringCase(text, "not informed")
                + countOccurrencesIgnoringCase(text, "not_informed"))
                .as("one NOT_INFORMED choice for every structured health question")
                .isGreaterThanOrEqualTo(8);
        assertThat(countOccurrencesIgnoringCase(text, "explanation"))
                .as("writable explanation control for every structured health question")
                .isGreaterThanOrEqualTo(8);
        assertContainsDeclarationConcepts(text);
        assertThat(countOccurrencesIgnoringCase(text, "rubrica")
                + countOccurrencesIgnoringCase(text, "initial"))
                .as("one writable initials field per declaration")
                .isGreaterThanOrEqualTo(6);
        assertThat(text.toLowerCase())
                .containsAnyOf("signature", "assinatura");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-012 - print-snapshot mode derives from immutable origin")
    void printSnapshotModeShouldDeriveFromOrigin() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Carlos", "Lima");
        UUID paperForm = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        UUID directForm = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));

        ExtractableResponse<Response> paperSnapshot = authenticatedJsonRequest(caller)
                .post(formPath(oratorianoId, paperForm) + "/print-snapshots")
                .then()
                .extract();
        ExtractableResponse<Response> directSnapshot = authenticatedJsonRequest(caller)
                .post(formPath(oratorianoId, directForm) + "/print-snapshots")
                .then()
                .extract();

        assertThat(paperSnapshot.statusCode()).isEqualTo(201);
        assertThat(paperSnapshot.<String>path("mode")).containsIgnoringCase("BLANK");
        assertThat(directSnapshot.statusCode()).isEqualTo(201);
        assertThat(directSnapshot.<String>path("mode")).containsIgnoringCase("PREFILL");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-002 - draft deletion hides draft-owned print snapshots atomically")
    void draftDeletionShouldHideOwnedArtifactsAtomically() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Lia", "D'Ávila");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        ExtractableResponse<Response> snapshot = authenticatedJsonRequest(caller)
                .post(formPath(oratorianoId, formId) + "/print-snapshots")
                .then()
                .statusCode(201)
                .extract();
        UUID snapshotId = UUID.fromString(snapshot.path("id"));
        clearActivities();

        ExtractableResponse<Response> deletion = authenticatedJsonRequest(caller)
                .body(reasonPayload("  Draft belongs to the wrong workflow  "))
                .delete(formPath(oratorianoId, formId))
                .then()
                .extract();

        assertThat(deletion.statusCode()).isEqualTo(204);
        assertThat(activityCountForTarget(formId)).isEqualTo(1);
        assertThat(authenticatedJsonRequest(caller)
                .get(formPath(oratorianoId, formId)).statusCode()).isEqualTo(404);
        assertThat(authenticatedJsonRequest(caller)
                .get(formPath(oratorianoId, formId) + "/print-snapshots/{snapshotId}/pdf", snapshotId)
                .statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-015 and REQ-ORATORIANO-FORM-016 - history is metadata-only while detail reads are audited")
    void historyShouldBeMetadataOnlyAndDetailReadShouldBeAudited() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Pedro", "Alves");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        clearActivities();

        ExtractableResponse<Response> history = authenticatedJsonRequest(caller)
                .get("/oratorianos/{id}/forms", oratorianoId)
                .then()
                .extract();

        assertThat(history.statusCode()).isEqualTo(200);
        List<Map<String, Object>> items = history.path("items");
        assertThat(items).hasSize(1);
        assertThat(items.getFirst())
                .containsKeys("id", "version", "status", "origin", "createdAt", "attachmentExists", "attachmentPageCount")
                .doesNotContainKeys(
                        "cpf", "rg", "address", "phoneNumber", "email", "family", "health",
                        "consent", "attachmentFilename", "digest", "bytes"
                );
        assertThat(activityLogCount()).isZero();

        ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                .get(formPath(oratorianoId, formId))
                .then()
                .extract();

        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(activityLogCount()).isEqualTo(1);
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT actor_account_id, target_id, metadata FROM activity_logs"
        );
        assertThat(audit)
                .containsEntry("actor_account_id", caller.accountId())
                .containsEntry("target_id", formId);
        assertThat(audit.get("metadata").toString())
                .contains(oratorianoId.toString())
                .doesNotContain("cpf", "rg", "health", "bytes");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-013 through REQ-ORATORIANO-FORM-016 - signed PDF -> private bytea persistence, metadata-only history, and audited authorized download")
    void signedPdfShouldPersistPrivatelyAndDownloadWithSensitiveReadAudit() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Alice", "Moraes");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        byte[] bytes = pdfBytes(79);
        clearActivities();

        ExtractableResponse<Response> upload = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed-form.pdf", "application/pdf", bytes))
        );

        assertThat(upload.statusCode()).isEqualTo(200);
        List<Map<String, Object>> uploaded = upload.jsonPath().getList("$");
        assertThat(uploaded).hasSize(1);
        Map<String, Object> metadata = uploaded.getFirst();
        UUID attachmentId = UUID.fromString(metadata.get("id").toString());
        assertUuidV7(attachmentId);
        assertThat(metadata)
                .containsEntry("originalFilename", "signed-form.pdf")
                .containsEntry("verifiedMimeType", "application/pdf")
                .containsEntry("byteLength", bytes.length)
                .containsEntry("pageOrder", 1)
                .doesNotContainKeys("sha256", "digest", "bytes", "url", "path");
        assertThat(activityCountForActionAndTarget(
                "ORATORIANO_FORM_ATTACHMENTS_REPLACED",
                formId
        )).isEqualTo(1);

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT original_filename, verified_mime_type, byte_length, page_order, sha256, bytes "
                        + "FROM oratoriano_form_attachments WHERE id = ? AND deleted_at IS NULL",
                attachmentId
        );
        assertThat(stored)
                .containsEntry("original_filename", "signed-form.pdf")
                .containsEntry("verified_mime_type", "application/pdf")
                .containsEntry("byte_length", (long) bytes.length)
                .containsEntry("page_order", 1)
                .containsEntry("sha256", sha256(bytes));
        assertThat((byte[]) stored.get("bytes")).containsExactly(bytes);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() "
                        + "AND table_name = 'oratoriano_form_attachments' AND column_name = 'bytes'",
                String.class
        )).isEqualTo("bytea");
        List<String> attachmentColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() "
                        + "AND table_name = 'oratoriano_form_attachments'",
                String.class
        );
        assertThat(attachmentColumns)
                .doesNotContain("url", "public_url", "path", "file_path", "filesystem_path");

        clearActivities();
        ExtractableResponse<Response> history = authenticatedJsonRequest(caller)
                .get("/oratorianos/{oratorianoId}/forms", oratorianoId)
                .then()
                .statusCode(200)
                .extract();
        Map<String, Object> historyItem = history.<List<Map<String, Object>>>path("items").getFirst();
        assertThat(historyItem)
                .containsEntry("attachmentExists", true)
                .containsEntry("attachmentPageCount", 1)
                .doesNotContainKeys(
                        "attachmentFilename", "originalFilename", "sha256", "digest",
                        "bytes", "url", "path"
                );
        assertThat(activityLogCount()).isZero();

        String downloadPath = formPath(oratorianoId, formId)
                + "/signed-attachments/" + attachmentId;
        AuthSession member = newSession("MEMBER");
        assertThat(jsonRequest().get(downloadPath).statusCode()).isEqualTo(401);
        assertThat(authenticatedJsonRequest(member).get(downloadPath).statusCode()).isEqualTo(403);
        assertThat(activityLogCount()).isZero();

        ExtractableResponse<Response> download = authenticatedJsonRequest(caller)
                .accept(ContentType.BINARY)
                .get(downloadPath)
                .then()
                .extract();

        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.contentType()).startsWith("application/pdf");
        assertThat(download.header("Content-Disposition"))
                .contains("attachment")
                .contains("signed-form.pdf");
        assertThat(download.asByteArray()).containsExactly(bytes);
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT actor_account_id, target_id, metadata "
                        + "FROM activity_logs "
                        + "WHERE action = 'ORATORIANO_FORM_ATTACHMENT_DOWNLOADED'"
        );
        assertThat(audit)
                .containsEntry("actor_account_id", caller.accountId())
                .containsEntry("target_id", formId);
        assertThat(audit.get("metadata").toString())
                .contains(oratorianoId.toString(), formId.toString(), attachmentId.toString())
                .doesNotContain("signed-form.pdf", sha256(bytes), "bytes", "JVBER");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-013 and REQ-ORATORIANO-FORM-014 - ten ordered JPEG/PNG pages accepted; eleven-page replacement rejected atomically")
    void orderedImagePageCountBoundariesShouldBeEnforcedAtomically() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Otávio", "Freitas");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        List<TestAttachment> tenPages = imagePages(10, 64);

        ExtractableResponse<Response> accepted = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                tenPages
        );

        assertThat(accepted.statusCode()).isEqualTo(200);
        List<Map<String, Object>> acceptedMetadata = accepted.jsonPath().getList("$");
        assertThat(acceptedMetadata).hasSize(10);
        assertThat(acceptedMetadata)
                .extracting(item -> ((Number) item.get("pageOrder")).intValue())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(acceptedMetadata)
                .extracting(item -> item.get("verifiedMimeType"))
                .contains("image/jpeg", "image/png");
        List<UUID> acceptedIds = activeAttachmentIds(formId);
        assertThat(acceptedIds).hasSize(10);
        clearActivities();

        ExtractableResponse<Response> rejected = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                imagePages(11, 64)
        );

        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(activeAttachmentIds(formId)).containsExactlyElementsOf(acceptedIds);
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-013 and REQ-ORATORIANO-FORM-014 - MIME mismatch -> HTTP 400 without partial attachment persistence")
    void declaredMimeShouldMatchAttachmentContent() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Nina", "Lopes");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        clearActivities();

        ExtractableResponse<Response> response = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("not-really-an-image.png", "image/png", pdfBytes(64)))
        );

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(activeAttachmentIds(formId)).isEmpty();
        assertThat(activityLogCount()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedMagicPrefixAttachmentCases")
    @DisplayName("REQ-ORATORIANO-FORM-013 - magic prefix without a parseable document or image -> atomic HTTP 400")
    void malformedMagicPrefixContentShouldBeRejected(
            String scenario,
            TestAttachment malformedAttachment
    ) {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Nina", "Lopes");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        clearActivities();

        ExtractableResponse<Response> response = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(malformedAttachment)
        );

        assertThat(response.statusCode()).as(scenario + ": " + response.asString()).isEqualTo(400);
        assertThat(activeAttachmentIds(formId)).isEmpty();
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-013 - PDF page count must match the declared print snapshot pages")
    void completionShouldUseParsedPdfPageCountInsteadOfAttachmentFileCount() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Caio", "Nunes");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        SnapshotPdf onePageSnapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        assertThat(onePageSnapshot.pageCount()).isEqualTo(1);
        authenticatedJsonRequest(caller)
                .body(validAdultSelfFormPayload())
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        assertThat(replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment(
                        "two-pages.pdf",
                        "application/pdf",
                        pdfWithPages(2)
                ))
        ).statusCode()).isEqualTo(200);
        clearActivities();

        ExtractableResponse<Response> completion = authenticatedJsonRequest(caller)
                .body(completionRequest(formId, false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(completion.statusCode()).as(completion.asString()).isEqualTo(400);
        assertThat(formStatus(formId)).isEqualTo("DRAFT");
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-016 - metadata history reports parsed PDF pages, not stored file count")
    void historyShouldReportTheActualPdfPageCount() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Caio", "Nunes");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        assertThat(replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment(
                        "three-pages.pdf",
                        "application/pdf",
                        pdfWithPages(3)
                ))
        ).statusCode()).isEqualTo(200);

        ExtractableResponse<Response> history = authenticatedJsonRequest(caller)
                .get("/oratorianos/{oratorianoId}/forms", oratorianoId)
                .then()
                .statusCode(200)
                .extract();

        assertThat(history.<Number>path("items[0].attachmentPageCount").intValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-013 - PDF size boundary -> 20 MiB accepted and 20 MiB plus one byte rejected")
    void pdfSizeBoundaryShouldBeEnforced() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Luiz", "Ramos");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));

        ExtractableResponse<Response> accepted = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment(
                        "twenty-mib.pdf",
                        "application/pdf",
                        pdfBytes(20 * MEBIBYTE)
                ))
        );

        assertThat(accepted.statusCode()).isEqualTo(200);
        UUID acceptedId = UUID.fromString(accepted.path("[0].id"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT byte_length FROM oratoriano_form_attachments WHERE id = ?",
                Long.class,
                acceptedId
        )).isEqualTo(20L * MEBIBYTE);
        clearActivities();

        ExtractableResponse<Response> rejected = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment(
                        "too-large.pdf",
                        "application/pdf",
                        pdfBytes(20 * MEBIBYTE + 1)
                ))
        );

        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(activeAttachmentIds(formId)).containsExactly(acceptedId);
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-013 - image boundaries -> 8 MiB per page and 40 MiB total accepted; one-byte excess rejected")
    void imageSizeBoundariesShouldBeEnforced() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Sofia", "Pereira");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));

        ExtractableResponse<Response> onePageAccepted = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("page.png", "image/png", pngBytes(8 * MEBIBYTE)))
        );
        assertThat(onePageAccepted.statusCode()).isEqualTo(200);
        UUID eightMibId = UUID.fromString(onePageAccepted.path("[0].id"));

        ExtractableResponse<Response> onePageRejected = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment(
                        "page-too-large.png",
                        "image/png",
                        pngBytes(8 * MEBIBYTE + 1)
                ))
        );
        assertThat(onePageRejected.statusCode()).isEqualTo(400);
        assertThat(activeAttachmentIds(formId)).containsExactly(eightMibId);

        ExtractableResponse<Response> totalAccepted = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                imagePages(5, 8 * MEBIBYTE)
        );
        assertThat(totalAccepted.statusCode()).isEqualTo(200);
        List<UUID> fortyMibIds = activeAttachmentIds(formId);
        assertThat(fortyMibIds).hasSize(5);
        clearActivities();

        List<TestAttachment> overFortyMib = new ArrayList<>();
        for (int page = 1; page <= 5; page++) {
            overFortyMib.add(new TestAttachment(
                    "page-" + page + ".png",
                    "image/png",
                    pngBytes(7 * MEBIBYTE)
            ));
        }
        overFortyMib.add(new TestAttachment(
                "page-6.png",
                "image/png",
                pngBytes(5 * MEBIBYTE + 1)
        ));

        ExtractableResponse<Response> totalRejected = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                overFortyMib
        );
        assertThat(totalRejected.statusCode()).isEqualTo(400);
        assertThat(activeAttachmentIds(formId)).containsExactlyElementsOf(fortyMibIds);
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-013 and REQ-ORATORIANO-FORM-019 - replacement soft-deletes old files and exposes only the new collection")
    void attachmentReplacementShouldSoftDeletePriorCollection() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Rafael", "Gomes");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        UUID oldAttachmentId = UUID.fromString(replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("old.pdf", "application/pdf", pdfBytes(64)))
        ).path("[0].id"));
        clearActivities();

        ExtractableResponse<Response> replacement = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("new.pdf", "application/pdf", pdfBytes(96)))
        );

        assertThat(replacement.statusCode()).isEqualTo(200);
        UUID newAttachmentId = UUID.fromString(replacement.path("[0].id"));
        assertThat(newAttachmentId).isNotEqualTo(oldAttachmentId);
        Map<String, Object> oldRow = jdbcTemplate.queryForMap(
                "SELECT deleted_at, deleted_by FROM oratoriano_form_attachments WHERE id = ?",
                oldAttachmentId
        );
        assertThat(oldRow)
                .containsEntry("deleted_by", caller.accountId());
        assertThat(oldRow.get("deleted_at")).isNotNull();
        assertThat(activeAttachmentIds(formId)).containsExactly(newAttachmentId);
        assertThat(authenticatedJsonRequest(caller)
                .get(formPath(oratorianoId, formId)
                        + "/signed-attachments/" + oldAttachmentId)
                .statusCode()).isEqualTo(404);
        assertThat(authenticatedJsonRequest(caller)
                .get(formPath(oratorianoId, formId)
                        + "/signed-attachments/" + newAttachmentId)
                .statusCode()).isEqualTo(200);
        assertThat(activityCountForActionAndTarget(
                "ORATORIANO_FORM_ATTACHMENTS_REPLACED",
                formId
        )).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MOTHER", "FATHER"})
    @DisplayName("REQ-ORATORIANO-FORM-004 through REQ-ORATORIANO-FORM-008 - minor parent-responsible completion -> canonical data and derived parent snapshot")
    void minorParentCompletionShouldDeriveParentAndStoreCanonicalValues(String relationship) {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Ana", "Silva");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> payload = validMinorParentPayload(relationship);

        authenticatedJsonRequest(caller)
                .body(payload)
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed.pdf", "application/pdf", snapshot.bytes()))
        );

        ExtractableResponse<Response> completion = authenticatedJsonRequest(caller)
                .body(completionRequest(formId, false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(completion.statusCode()).as(completion.asString()).isEqualTo(204);
        ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                .get(formPath(oratorianoId, formId))
                .then()
                .statusCode(200)
                .extract();
        assertThat(detail.<String>path("data.firstName")).isEqualTo("Ana");
        assertThat(detail.<String>path("data.address.addressLine")).isEqualTo("Rua das Flores");
        assertThat(detail.<String>path("data.address.cep")).isEqualTo("13400000");
        String parent = relationship.equals("MOTHER") ? "mother" : "father";
        String parentFirstName = relationship.equals("MOTHER") ? "Maria" : "João";
        assertThat(detail.<String>path("data.responsible.firstName")).isEqualTo(parentFirstName);
        assertThat(detail.<String>path("data." + parent + ".firstName")).isEqualTo(parentFirstName);
        assertThat(detail.<String>path("data." + parent + ".surname")).isEqualTo("Silva");
        assertThat(detail.<String>path("data." + parent + ".cpf")).isEqualTo("11144477735");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "minor missing school",
            "responsible adult not confirmed",
            "relative missing complement",
            "YES health answer missing explanation",
            "NO health answer with contradictory explanation",
            "incomplete parent snapshot",
            "mandatory declaration refused"
    })
    @DisplayName("REQ-ORATORIANO-FORM-003 through REQ-ORATORIANO-FORM-009 - invalid conditional matrix -> atomic completion rejection")
    void invalidConditionalCompletionMatrixShouldRemainDraft(String scenario) {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Ana", "Silva");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> payload = invalidConditionalPayload(scenario);
        authenticatedJsonRequest(caller)
                .body(payload)
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed.pdf", "application/pdf", snapshot.bytes()))
        );
        clearActivities();

        ExtractableResponse<Response> completion = authenticatedJsonRequest(caller)
                .body(completionRequest(formId, false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(completion.statusCode()).as(scenario + ": " + completion.asString()).isEqualTo(400);
        assertThat(formStatus(formId)).isEqualTo("DRAFT");
        assertThat(activityLogCount()).isZero();
        authenticatedJsonRequest(caller)
                .get("/oratorianos/{oratorianoId}", oratorianoId)
                .then()
                .statusCode(200)
                .body("firstName", org.hamcrest.Matchers.equalTo("Ana"))
                .body("surname", org.hamcrest.Matchers.equalTo("Silva"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"YES", "NOT_INFORMED"})
    @DisplayName("REQ-ORATORIANO-FORM-008 - accepted structured health answers -> valid completion")
    void acceptedStructuredHealthAnswersShouldComplete(String answer) {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> payload = validAdultSelfFormPayload();
        Map<String, Object> health = validHealthPayload();
        health.put(
                "medicalFollowUp",
                answer.equals("YES")
                        ? Map.of("answer", "YES", "explanation", "Annual clinical follow-up")
                        : Map.of("answer", "NOT_INFORMED")
        );
        health.put(
                "medicineUse",
                Map.of(
                        "answer", "YES",
                        "explanation", "Prescribed daily medicine",
                        "importantInstructions", "Keep the medicine with the responsible adult"
                )
        );
        payload.put("health", health);
        authenticatedJsonRequest(caller)
                .body(payload)
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed.pdf", "application/pdf", snapshot.bytes()))
        );

        authenticatedJsonRequest(caller)
                .body(completionRequest(formId, false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .statusCode(204);
        assertThat(formStatus(formId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-005 and REQ-ORATORIANO-FORM-006 - exactly eighteen with SELF -> derived responsible snapshot")
    void exactlyEighteenSelfResponsibleShouldDeriveIdentityWithoutContradictoryDuplicates() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> payload = validAdultSelfFormPayload();
        payload.put("birthDate", "2008-07-20");
        payload.put("responsible", Map.of(
                "relationship", "SELF",
                "firstName", "Contradictory",
                "surname", "Duplicate",
                "cpf", "11144477735",
                "phoneNumber", "+5519999999999",
                "atLeast18", true
        ));
        authenticatedJsonRequest(caller)
                .body(payload)
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed.pdf", "application/pdf", snapshot.bytes()))
        );

        authenticatedJsonRequest(caller)
                .body(completionRequest(formId, false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .statusCode(204);
        ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                .get(formPath(oratorianoId, formId))
                .then()
                .statusCode(200)
                .extract();

        assertThat(detail.<String>path("data.responsible.firstName")).isEqualTo("Erik");
        assertThat(detail.<String>path("data.responsible.surname")).isEqualTo("Garcia");
        assertThat(detail.<String>path("data.responsible.cpf")).isEqualTo("52998224725");
        assertThat(detail.<String>path("data.responsible.phoneNumber")).isEqualTo("+5519998877665");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-006 and REQ-ORATORIANO-FORM-017 - profile corrected after signedOn -> completion requires explicit overwrite choice")
    void formCompletionShouldNotSilentlyOverwriteNewerManualProfileValues() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> payload = validAdultSelfFormPayload();
        payload.put("firstName", "Signed");
        payload.put("surname", "Version");
        authenticatedJsonRequest(caller)
                .body(payload)
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed.pdf", "application/pdf", snapshot.bytes()))
        );

        authenticatedJsonRequest(caller)
                .body(oratorianoReplacementPayload(
                        "Current",
                        "Correction",
                        "1999-01-02",
                        "+5519991112233",
                        "Corrected after the paper form was signed"
                ))
                .put("/oratorianos/{oratorianoId}", oratorianoId)
                .then()
                .statusCode(200);
        clearActivities();

        ExtractableResponse<Response> completion = authenticatedJsonRequest(caller)
                .body(completionRequest(formId, false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(completion.statusCode()).as(completion.asString()).isEqualTo(409);
        assertThat(completion.<String>path("code"))
                .isEqualTo("ORATORIANO_FORM_PROFILE_OVERWRITE_CHOICE_REQUIRED");
        assertThat(formStatus(formId)).isEqualTo("DRAFT");
        authenticatedJsonRequest(caller)
                .get("/oratorianos/{oratorianoId}", oratorianoId)
                .then()
                .statusCode(200)
                .body("firstName", org.hamcrest.Matchers.equalTo("Current"))
                .body("surname", org.hamcrest.Matchers.equalTo("Correction"))
                .body("birthDate", org.hamcrest.Matchers.equalTo("1999-01-02"))
                .body("phoneNumber", org.hamcrest.Matchers.equalTo("+5519991112233"));
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-006 and REQ-ORATORIANO-FORM-017 - older signed form versus later registration -> explicit authorized overwrite choice")
    void formSignedBeforeRegistrationShouldRequireAndHonorExplicitOverwriteChoice() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Registration", "Name");
        UUID formId = readyAdultDraft(
                caller,
                oratorianoId,
                "Older Signed",
                "Name",
                "2026-07-20"
        );
        clearActivities();

        ExtractableResponse<Response> withoutChoice = authenticatedJsonRequest(caller)
                .body(completionRequest(formId, false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(withoutChoice.statusCode()).as(withoutChoice.asString()).isEqualTo(409);
        assertThat(withoutChoice.<String>path("code"))
                .isEqualTo("ORATORIANO_FORM_PROFILE_OVERWRITE_CHOICE_REQUIRED");
        assertThat(formStatus(formId)).isEqualTo("DRAFT");
        authenticatedJsonRequest(caller)
                .get("/oratorianos/{oratorianoId}", oratorianoId)
                .then()
                .statusCode(200)
                .body("firstName", org.hamcrest.Matchers.equalTo("Registration"))
                .body("surname", org.hamcrest.Matchers.equalTo("Name"));
        assertThat(activityLogCount()).isZero();

        ExtractableResponse<Response> withChoice = authenticatedJsonRequest(caller)
                .body(completionRequest(formId, true))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(withChoice.statusCode()).as(withChoice.asString()).isEqualTo(204);
        assertThat(formStatus(formId)).isEqualTo("COMPLETED");
        authenticatedJsonRequest(caller)
                .get("/oratorianos/{oratorianoId}", oratorianoId)
                .then()
                .statusCode(200)
                .body("firstName", org.hamcrest.Matchers.equalTo("Older Signed"))
                .body("surname", org.hamcrest.Matchers.equalTo("Name"))
                .body("birthDate", org.hamcrest.Matchers.equalTo("2000-05-20"))
                .body("phoneNumber", org.hamcrest.Matchers.equalTo("+5519998877665"));
        assertThat(activityCountForTarget(formId)).isEqualTo(1);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-006 - older signed form -> cannot replace values sourced from a later completed form")
    void signedDatePrecedenceShouldProtectLaterFormSourcedProfileValues() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        UUID laterForm = readyAdultDraft(
                caller,
                oratorianoId,
                "Later",
                "Source",
                "2026-07-20"
        );
        authenticatedJsonRequest(caller)
                .body(completionRequest(laterForm, true))
                .patch(formPath(oratorianoId, laterForm) + "/complete")
                .then()
                .statusCode(204);

        UUID olderForm = readyAdultDraft(
                caller,
                oratorianoId,
                "Older",
                "Source",
                "2026-07-19"
        );
        clearActivities();
        ExtractableResponse<Response> completion = authenticatedJsonRequest(caller)
                .body(completionRequest(olderForm, false))
                .patch(formPath(oratorianoId, olderForm) + "/complete")
                .then()
                .extract();

        assertThat(completion.statusCode()).as(completion.asString()).isEqualTo(409);
        assertThat(completion.<String>path("code"))
                .isEqualTo("ORATORIANO_FORM_PROFILE_SOURCE_IS_NEWER");
        assertThat(formStatus(olderForm)).isEqualTo("DRAFT");
        authenticatedJsonRequest(caller)
                .get("/oratorianos/{oratorianoId}", oratorianoId)
                .then()
                .statusCode(200)
                .body("firstName", org.hamcrest.Matchers.equalTo("Later"))
                .body("surname", org.hamcrest.Matchers.equalTo("Source"));
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-003, REQ-ORATORIANO-FORM-017 and ADR-0017 - completion versus profile deletion -> serialized domain outcomes without deadlock")
    void completionAndOratorianoDeletionShouldSerializeWithoutPersistenceFailure() throws Exception {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        UUID formId = readyAdultDraft(caller, oratorianoId, "Erik", "Garcia", "2026-07-20");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try (Connection blocker = jdbcTemplate.getDataSource().getConnection();
             PreparedStatement lockForm = blocker.prepareStatement(
                     "SELECT id FROM oratoriano_additional_forms WHERE id = ? FOR UPDATE"
             )) {
            blocker.setAutoCommit(false);
            lockForm.setObject(1, formId);
            try (var ignored = lockForm.executeQuery()) {
                assertThat(ignored.next()).isTrue();
            }

            Future<ExtractableResponse<Response>> completion = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .body(completionRequest(formId, false))
                            .patch(formPath(oratorianoId, formId) + "/complete")
                            .then()
                            .extract()
            );
            awaitMutationLockWaiters(1);
            Future<ExtractableResponse<Response>> deletion = executor.submit(() ->
                    authenticatedJsonRequest(caller)
                            .body(reasonPayload("Removing the erroneous Oratoriano"))
                            .delete("/oratorianos/{oratorianoId}", oratorianoId)
                            .then()
                            .extract()
            );
            awaitMutationLockWaiters(2);

            blocker.commit();
            ExtractableResponse<Response> completionResponse =
                    completion.get(15, TimeUnit.SECONDS);
            ExtractableResponse<Response> deletionResponse =
                    deletion.get(15, TimeUnit.SECONDS);
            List<Integer> statuses = List.of(
                    completionResponse.statusCode(),
                    deletionResponse.statusCode()
            );

            assertThat(statuses)
                    .allSatisfy(status -> assertThat(status).isBetween(200, 499))
                    .contains(204, 409);
            assertThat(formStatus(formId)).isIn("COMPLETED", "DRAFT");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-012 - signed PDF from a superseded print snapshot -> completion rejection")
    void directEntryCompletionShouldRequireTheLatestPrintSnapshot() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        authenticatedJsonRequest(caller)
                .body(validAdultSelfFormPayload())
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf olderSnapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        SnapshotPdf latestSnapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        assertThat(latestSnapshot.id()).isNotEqualTo(olderSnapshot.id());
        replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed-old-snapshot.pdf", "application/pdf", olderSnapshot.bytes()))
        );

        ExtractableResponse<Response> completion = authenticatedJsonRequest(caller)
                .body(completionRequestForSnapshot(latestSnapshot.id(), false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(completion.statusCode()).as(completion.asString()).isEqualTo(400);
        assertThat(formStatus(formId)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-012 and REQ-ORATORIANO-FORM-013 - declared attachment pages must match the print snapshot")
    void completionShouldRejectAttachmentPageCountDifferentFromSnapshot() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        authenticatedJsonRequest(caller)
                .body(validAdultSelfFormPayload())
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        assertThat(snapshot.pageCount()).isEqualTo(1);
        replaceAttachments(caller, oratorianoId, formId, imagePages(2, 64));

        ExtractableResponse<Response> completion = authenticatedJsonRequest(caller)
                .body(completionRequest(formId, false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(completion.statusCode()).as(completion.asString()).isEqualTo(400);
        assertThat(formStatus(formId)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-016 - history -> lifecycle actors and real page boundaries")
    void metadataHistoryShouldExposeActorsAndHonorPaging() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Lia", "D'Ávila");
        UUID first = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        UUID second = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        UUID third = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));

        ExtractableResponse<Response> firstPage = authenticatedJsonRequest(caller)
                .queryParam("page", 0)
                .queryParam("size", 1)
                .get("/oratorianos/{oratorianoId}/forms", oratorianoId)
                .then()
                .extract();
        ExtractableResponse<Response> secondPage = authenticatedJsonRequest(caller)
                .queryParam("page", 1)
                .queryParam("size", 1)
                .get("/oratorianos/{oratorianoId}/forms", oratorianoId)
                .then()
                .extract();

        assertThat(firstPage.statusCode()).isEqualTo(200);
        assertThat(secondPage.statusCode()).isEqualTo(200);
        assertThat(firstPage.<List<Map<String, Object>>>path("items")).hasSize(1);
        assertThat(secondPage.<List<Map<String, Object>>>path("items")).hasSize(1);
        assertThat(firstPage.<Number>path("page").intValue()).isZero();
        assertThat(secondPage.<Number>path("page").intValue()).isEqualTo(1);
        assertThat(firstPage.<Number>path("size").intValue()).isEqualTo(1);
        assertThat(firstPage.<Number>path("totalElements").longValue()).isEqualTo(3);
        assertThat(List.of(
                firstPage.<String>path("items[0].id"),
                secondPage.<String>path("items[0].id")
        )).doesNotHaveDuplicates();
        assertThat(firstPage.<String>path("items[0].createdBy.id"))
                .isEqualTo(caller.accountId().toString());
        assertThat(List.of(first, second, third)).hasSize(3);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-016 and REQ-ORATORIANO-FORM-018 - revoked history -> creation, completion, and revocation actors with timestamps")
    void revokedHistoryShouldExposeAllLifecycleActorsAndTimestamps() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Renata", "Nunes");
        UUID formId = readyAdultDraft(caller, oratorianoId, "Renata", "Nunes", "2026-07-20");
        authenticatedJsonRequest(caller)
                .body(completionRequest(formId, false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .statusCode(204);
        authenticatedJsonRequest(caller)
                .body(reasonPayload("Image authorization withdrawn"))
                .patch(formPath(oratorianoId, formId) + "/revoke")
                .then()
                .statusCode(204);

        ExtractableResponse<Response> history = authenticatedJsonRequest(caller)
                .get("/oratorianos/{oratorianoId}/forms", oratorianoId)
                .then()
                .statusCode(200)
                .extract();

        assertThat(history.<String>path("items[0].status")).isEqualTo("REVOKED");
        assertThat(history.<String>path("items[0].createdBy.id")).isEqualTo(caller.accountId().toString());
        assertThat(history.<String>path("items[0].completedBy.id")).isEqualTo(caller.accountId().toString());
        assertThat(history.<String>path("items[0].revokedBy.id")).isEqualTo(caller.accountId().toString());
        assertThat(history.<String>path("items[0].createdAt")).isNotBlank();
        assertThat(history.<String>path("items[0].completedAt")).isNotBlank();
        assertThat(history.<String>path("items[0].revokedAt")).isNotBlank();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-002 and REQ-ORATORIANO-FORM-018 - DRAFT cannot be revoked")
    void draftShouldNotBeRevocable() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Renata", "Nunes");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        clearActivities();

        ExtractableResponse<Response> response = authenticatedJsonRequest(caller)
                .body(reasonPayload("Consent withdrawn"))
                .patch(formPath(oratorianoId, formId) + "/revoke")
                .then()
                .extract();

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(formStatus(formId)).isEqualTo("DRAFT");
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-015 - form, PDF, and attachment permissions remain separate from ordinary profile access")
    void formPermissionsShouldRemainSeparateFromOrdinaryProfileAccess() {
        AuthSession setup = sudoSession();
        UUID oratorianoId = createOratoriano(setup, "Bianca", "Rocha");
        UUID formId = draftId(createDraft(setup, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        AuthSession member = newSession("MEMBER");

        assertThat(jsonRequest().get(formPath(oratorianoId, formId)).statusCode()).isEqualTo(401);
        assertThat(authenticatedJsonRequest(member)
                .get(formPath(oratorianoId, formId)).statusCode()).isEqualTo(403);
        assertThat(authenticatedJsonRequest(member)
                .body(Map.of("origin", "DIRECT_SYSTEM_ENTRY"))
                .post("/oratorianos/{id}/forms", oratorianoId).statusCode()).isEqualTo(403);
        assertThat(authenticatedJsonRequest(member)
                .post(formPath(oratorianoId, formId) + "/print-snapshots").statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-003 through REQ-ORATORIANO-FORM-010 and REQ-ORATORIANO-FORM-017 - valid adult draft completion -> current immutable form and synchronized profile")
    void validAdultDraftCompletionShouldBecomeCurrentAndSynchronizeProfile() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));

        ExtractableResponse<Response> saved = authenticatedJsonRequest(caller)
                .body(validAdultSelfFormPayload())
                .put(formPath(oratorianoId, formId))
                .then()
                .extract();
        assertThat(saved.statusCode()).isEqualTo(200);

        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        UUID attachmentId = UUID.fromString(replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed-form.pdf", "application/pdf", snapshot.bytes()))
        ).path("[0].id"));
        clearActivities();

        ExtractableResponse<Response> completion = authenticatedJsonRequest(caller)
                .body(completionRequest(formId, false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(completion.statusCode()).isEqualTo(204);
        assertThat(formStatus(formId)).isEqualTo("COMPLETED");
        Map<String, Object> completed = jdbcTemplate.queryForMap(
                "SELECT completed_at, completed_by FROM oratoriano_additional_forms WHERE id = ?",
                formId
        );
        assertThat(completed.get("completed_at")).isNotNull();
        assertThat(completed.get("completed_by")).isEqualTo(caller.accountId());
        assertThat(activityCountForTarget(formId)).isEqualTo(1);

        ExtractableResponse<Response> profile = authenticatedJsonRequest(caller)
                .get("/oratorianos/{id}", oratorianoId)
                .then()
                .statusCode(200)
                .extract();
        assertThat(profile.<String>path("firstName")).isEqualTo("Erik");
        assertThat(profile.<String>path("surname")).isEqualTo("Garcia");
        assertThat(profile.<String>path("birthDate")).isEqualTo("2000-05-20");
        assertThat(profile.<String>path("phoneNumber")).isEqualTo("+5519998877665");

        ExtractableResponse<Response> history = authenticatedJsonRequest(caller)
                .get("/oratorianos/{id}/forms", oratorianoId)
                .then()
                .statusCode(200)
                .extract();
        assertThat(history.<String>path("items[0].status")).isEqualTo("COMPLETED");
        assertThat(history.<String>path("items[0].signedOn")).isEqualTo("2026-07-20");
        assertThat(history.<String>path("items[0].createdBy.id")).isEqualTo(caller.accountId().toString());
        assertThat(history.<String>path("items[0].completedBy.id")).isEqualTo(caller.accountId().toString());
        assertThat(history.<Boolean>path("items[0].attachmentExists")).isTrue();
        assertThat(history.<Number>path("items[0].attachmentPageCount").intValue()).isEqualTo(1);

        ExtractableResponse<Response> immutableReplacement = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("replacement.pdf", "application/pdf", pdfBytes(160)))
        );
        assertThat(immutableReplacement.statusCode()).isEqualTo(409);
        assertThat(activeAttachmentIds(formId)).containsExactly(attachmentId);
        assertThat(activityCountForTarget(formId)).isEqualTo(1);
    }

    private ExtractableResponse<Response> createDraft(
            AuthSession caller,
            UUID oratorianoId,
            String origin
    ) {
        return authenticatedJsonRequest(caller)
                .body(Map.of("origin", origin))
                .post("/oratorianos/{id}/forms", oratorianoId)
                .then()
                .extract();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"spaced text", "unbroken text"})
    @DisplayName("REQ-ORATORIANO-FORM-011 - maximum bounded care text remains visible in a paginated printable PDF")
    void longCareTextShouldWrapInsidePrintableMultiPageLayout(String layout) throws IOException {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Marina", "Sousa");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> payload = validAdultSelfFormPayload();
        Map<String, Object> health = validHealthPayload();
        String longCare = layout.equals("unbroken text")
                ? "x".repeat(5_000)
                : "Cuidado continuo ".repeat(313).substring(0, 5_000);
        assertThat(longCare).hasSize(5_000);
        health.put("otherCare", longCare);
        payload.put("health", health);
        authenticatedJsonRequest(caller)
                .body(payload)
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);

        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);

        assertThat(snapshot.pageCount())
                .as("all bounded fields must fit in a legible print-ready layout")
                .isGreaterThan(1);
        assertPrintReadyTextLayout(snapshot.bytes(), snapshot.pageCount());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "medicalFollowUp",
            "physicalActivityRestriction",
            "allergies",
            "convulsions",
            "frequentFainting",
            "heartCondition",
            "otherHealthCondition"
    })
    @DisplayName("REQ-ORATORIANO-FORM-008 - important instructions are exclusive to medicine use")
    void nonMedicineHealthQuestionsShouldRejectImportantInstructions(String question) {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Ana", "Silva");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> payload = validAdultSelfFormPayload();
        Map<String, Object> health = validHealthPayload();
        health.put(question, Map.of(
                "answer", "NO",
                "importantInstructions", "Unsupported instructions"
        ));
        payload.put("health", health);
        authenticatedJsonRequest(caller)
                .body(payload)
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed.pdf", "application/pdf", snapshot.bytes()))
        );
        clearActivities();

        ExtractableResponse<Response> completion = authenticatedJsonRequest(caller)
                .body(completionRequest(formId, true))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(completion.statusCode()).as(completion.asString()).isEqualTo(400);
        assertThat(formStatus(formId)).isEqualTo("DRAFT");
        assertThat(activityLogCount()).isZero();
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-005 - SELF responsible preserves the supplied optional email")
    void selfResponsibleShouldPreserveOptionalEmail() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> payload = validAdultSelfFormPayload();
        payload.put("responsible", Map.of(
                "relationship", "SELF",
                "email", "erik.responsible@example.com",
                "atLeast18", true
        ));
        authenticatedJsonRequest(caller)
                .body(payload)
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed.pdf", "application/pdf", snapshot.bytes()))
        );

        authenticatedJsonRequest(caller)
                .body(completionRequest(formId, false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .statusCode(204);
        ExtractableResponse<Response> detail = authenticatedJsonRequest(caller)
                .get(formPath(oratorianoId, formId))
                .then()
                .statusCode(200)
                .extract();

        assertThat(detail.<String>path("data.responsible.email"))
                .isEqualTo("erik.responsible@example.com");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-006 - absent optional minor phone does not erase ordinary profile data")
    void absentMinorPhoneShouldNotOverwriteAnOrdinaryProfilePhone() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Ana", "Silva");
        authenticatedJsonRequest(caller)
                .body(oratorianoReplacementPayload(
                        "Ana",
                        "Silva",
                        "2010-07-21",
                        "+5519991112233",
                        "Recording the ordinary profile phone"
                ))
                .put("/oratorianos/{oratorianoId}", oratorianoId)
                .then()
                .statusCode(200);
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> payload = validMinorParentPayload("MOTHER");
        assertThat(payload).doesNotContainKey("phoneNumber");
        authenticatedJsonRequest(caller)
                .body(payload)
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed.pdf", "application/pdf", snapshot.bytes()))
        );

        authenticatedJsonRequest(caller)
                .body(completionRequest(formId, true))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .statusCode(204);
        ExtractableResponse<Response> profile = authenticatedJsonRequest(caller)
                .get("/oratorianos/{oratorianoId}", oratorianoId)
                .then()
                .statusCode(200)
                .extract();
        Map<String, Object> phoneProvenance = jdbcTemplate.queryForMap(
                "SELECT phone_source_form_id, phone_source_signed_on "
                        + "FROM oratorianos WHERE id = ?",
                oratorianoId
        );

        assertThat(profile.<String>path("phoneNumber")).isEqualTo("+5519991112233");
        assertThat(phoneProvenance)
                .containsEntry("phone_source_form_id", null)
                .containsEntry("phone_source_signed_on", null);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-006 - absent phone leaves no provenance barrier to an older supplied value")
    void olderFormShouldFillPhoneLeftMissingByANewerForm() {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Ana", "Silva");

        UUID newerForm = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> newerPayload = validMinorParentPayload("MOTHER");
        assertThat(newerPayload).doesNotContainKey("phoneNumber");
        authenticatedJsonRequest(caller)
                .body(newerPayload)
                .put(formPath(oratorianoId, newerForm))
                .then()
                .statusCode(200);
        SnapshotPdf newerSnapshot = createSnapshotAndRender(caller, oratorianoId, newerForm);
        replaceAttachments(
                caller,
                oratorianoId,
                newerForm,
                List.of(new TestAttachment("newer-signed.pdf", "application/pdf", newerSnapshot.bytes()))
        );
        authenticatedJsonRequest(caller)
                .body(completionRequest(newerForm, true))
                .patch(formPath(oratorianoId, newerForm) + "/complete")
                .then()
                .statusCode(204);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT phone_source_form_id FROM oratorianos WHERE id = ?",
                UUID.class,
                oratorianoId
        )).isNull();

        UUID olderForm = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> olderPayload = validMinorParentPayload("MOTHER");
        olderPayload.put("phoneNumber", "+5519991112233");
        olderPayload.put("signedOn", "2026-07-19");
        authenticatedJsonRequest(caller)
                .body(olderPayload)
                .put(formPath(oratorianoId, olderForm))
                .then()
                .statusCode(200);
        SnapshotPdf olderSnapshot = createSnapshotAndRender(caller, oratorianoId, olderForm);
        replaceAttachments(
                caller,
                oratorianoId,
                olderForm,
                List.of(new TestAttachment("older-signed.pdf", "application/pdf", olderSnapshot.bytes()))
        );

        authenticatedJsonRequest(caller)
                .body(completionRequest(olderForm, false))
                .patch(formPath(oratorianoId, olderForm) + "/complete")
                .then()
                .statusCode(204);
        ExtractableResponse<Response> profile = authenticatedJsonRequest(caller)
                .get("/oratorianos/{oratorianoId}", oratorianoId)
                .then()
                .statusCode(200)
                .extract();

        assertThat(profile.<String>path("phoneNumber")).isEqualTo("+5519991112233");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT phone_source_form_id FROM oratorianos WHERE id = ?",
                UUID.class,
                oratorianoId
        )).isEqualTo(olderForm);
    }

    @Test
    @DisplayName("REQ-OPENAPI-007 - form history accepts size 100 and rejects size above 100")
    void formHistoryShouldRejectOversizedPages() {
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Lia", "Garcia");
        draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));

        assertThat(authenticatedJsonRequest(caller)
                .queryParam("size", 100)
                .get("/oratorianos/{oratorianoId}/forms", oratorianoId)
                .statusCode()).isEqualTo(200);
        assertThat(authenticatedJsonRequest(caller)
                .queryParam("size", 101)
                .get("/oratorianos/{oratorianoId}/forms", oratorianoId)
                .statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-012 and REQ-ORATORIANO-FORM-013 - direct entry accepts a scanned PDF bound to the latest snapshot")
    void directEntryShouldAcceptAnImageOnlyScannedPdfForTheSelectedLatestSnapshot()
            throws IOException {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        authenticatedJsonRequest(caller)
                .body(validAdultSelfFormPayload())
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        byte[] scannedPdf = rasterizedPdfScan(snapshot.bytes());
        assertThat(pdfText(scannedPdf)).isBlank();
        assertThat(replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment(
                        "signed-scanned-form.pdf",
                        "application/pdf",
                        scannedPdf
                ))
        ).statusCode()).isEqualTo(200);

        ExtractableResponse<Response> completion = authenticatedJsonRequest(caller)
                .body(completionRequestForSnapshot(snapshot.id(), false))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(completion.statusCode()).as(completion.asString()).isEqualTo(204);
        assertThat(formStatus(formId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-012 - paper completion requires and honors its selected non-latest snapshot")
    void paperCompletionShouldRequireAndHonorSelectedSnapshotIdentity() throws IOException {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Ana", "Silva");
        UUID formId = draftId(createDraft(caller, oratorianoId, "PAPER_TRANSCRIPTION"));
        SnapshotPdf signedBlank = createSnapshotAndRender(caller, oratorianoId, formId);
        authenticatedJsonRequest(caller)
                .body(validMinorParentPayload("MOTHER"))
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf laterSnapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        assertThat(laterSnapshot.id()).isNotEqualTo(signedBlank.id());
        byte[] scannedPdf = rasterizedPdfScan(signedBlank.bytes());
        assertThat(replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("returned-paper.pdf", "application/pdf", scannedPdf))
        ).statusCode()).isEqualTo(200);
        clearActivities();

        ExtractableResponse<Response> missingSelection = authenticatedJsonRequest(caller)
                .body(Map.of("overwriteNewerProfileValues", true))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(missingSelection.statusCode()).as(missingSelection.asString()).isEqualTo(400);
        assertThat(formStatus(formId)).isEqualTo("DRAFT");
        assertThat(activityLogCount()).isZero();

        ExtractableResponse<Response> selectedOriginal = authenticatedJsonRequest(caller)
                .body(completionRequestForSnapshot(signedBlank.id(), true))
                .patch(formPath(oratorianoId, formId) + "/complete")
                .then()
                .extract();

        assertThat(selectedOriginal.statusCode())
                .as(selectedOriginal.asString())
                .isEqualTo(204);
        assertThat(formStatus(formId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("REQ-ORATORIANO-FORM-004, REQ-ORATORIANO-FORM-008 and REQ-ORATORIANO-FORM-011 - valid Unicode renders without server failure")
    void acceptedUnicodeShouldRenderInTheGeneratedPdf() throws IOException {
        setCurrentInstant(Instant.parse("2026-07-25T15:00:00Z"));
        AuthSession caller = sudoSession();
        UUID oratorianoId = createOratoriano(caller, "Erik", "Garcia");
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> payload = validAdultSelfFormPayload();
        Map<String, Object> health = validHealthPayload();
        String unicodeCare = "Cuidados: Δοκιμή Жизнь";
        health.put("otherCare", unicodeCare);
        payload.put("health", health);
        authenticatedJsonRequest(caller)
                .body(payload)
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);

        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);

        assertThat(pdfText(snapshot.bytes())).contains(unicodeCare);
        assertPrintReadyTextLayout(snapshot.bytes(), snapshot.pageCount());
    }

    private static UUID draftId(ExtractableResponse<Response> response) {
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(response.path("id"));
    }

    private static String formPath(UUID oratorianoId, UUID formId) {
        return "/oratorianos/" + oratorianoId + "/forms/" + formId;
    }

    private long formCount(UUID oratorianoId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oratoriano_additional_forms WHERE oratoriano_id = ?",
                Long.class,
                oratorianoId
        );
    }

    private String formStatus(UUID formId) {
        return jdbcTemplate.queryForObject(
                "SELECT status::text FROM oratoriano_additional_forms WHERE id = ?",
                String.class,
                formId
        );
    }

    private long activityLogCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_logs", Long.class);
    }

    private Map<String, Object> validAdultSelfFormPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("firstName", "Erik");
        payload.put("surname", "Garcia");
        payload.put("birthDate", "2000-05-20");
        payload.put("cpf", "52998224725");
        payload.put("address", Map.of(
                "addressLine", "Rua São José",
                "addressNumber", "150 fundos",
                "neighborhood", "Centro",
                "cep", "13400000",
                "city", "Piracicaba"
        ));
        payload.put("phoneNumber", "+5519998877665");
        payload.put("responsible", Map.of(
                "relationship", "SELF",
                "atLeast18", true
        ));
        payload.put("health", validHealthPayload());
        payload.put("declarations", validDeclarationsPayload());
        payload.put("signedOn", "2026-07-20");
        return payload;
    }

    private Map<String, Object> completeAccentedAdultFormPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("firstName", "Érik");
        payload.put("surname", "García");
        payload.put("birthDate", "2000-05-20");
        payload.put("cpf", "52998224725");
        payload.put("rg", "45.678.901-2");
        payload.put("address", Map.of(
                "addressLine", "Rua São José",
                "addressNumber", "150 fundos",
                "neighborhood", "Jardim Água Branca",
                "cep", "13400-000",
                "city", "Limeira"
        ));
        payload.put("phoneNumber", "+5519998877665");
        payload.put("schoolName", "Escola São José");
        payload.put("schoolGrade", "9º ano");
        payload.put("responsible", Map.of(
                "relationship", "SELF",
                "atLeast18", true
        ));
        payload.put("father", Map.of(
                "firstName", "João",
                "surname", "D'Ávila",
                "cpf", "11144477735"
        ));
        payload.put("mother", Map.of(
                "firstName", "Ana",
                "surname", "Luísa",
                "cpf", "12345678909"
        ));
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("medicalFollowUp", Map.of(
                "answer", "YES",
                "explanation", "Cardiologista Dr. José"
        ));
        health.put("physicalActivityRestriction", Map.of(
                "answer", "YES",
                "explanation", "Evitar esforço intenso"
        ));
        health.put("medicineUse", Map.of(
                "answer", "YES",
                "explanation", "Anti-histamínico",
                "importantInstructions", "Tomar após o almoço"
        ));
        health.put("allergies", Map.of(
                "answer", "YES",
                "explanation", "Amendoim e castanhas"
        ));
        health.put("convulsions", Map.of("answer", "NO"));
        health.put("frequentFainting", Map.of("answer", "NOT_INFORMED"));
        health.put("heartCondition", Map.of("answer", "NO"));
        health.put("otherHealthCondition", Map.of("answer", "NO"));
        health.put("otherCare", "Levar medicação de emergência");
        payload.put("health", health);
        payload.put("declarations", validDeclarationsPayload());
        payload.put("signedOn", "2026-07-20");
        return payload;
    }

    private Map<String, Object> validMinorParentPayload(String relationship) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("firstName", "  Ana  ");
        payload.put("surname", "  Silva  ");
        payload.put("birthDate", "2010-07-21");
        payload.put("cpf", "529.982.247-25");
        payload.put("address", Map.of(
                "addressLine", "  Rua das Flores  ",
                "addressNumber", "  12A  ",
                "neighborhood", "  Centro  ",
                "cep", "13400-000",
                "city", "  Piracicaba  "
        ));
        payload.put("schoolName", "  Escola Municipal  ");
        payload.put("schoolGrade", "  9º ano  ");
        payload.put("responsible", Map.of(
                "relationship", relationship,
                "firstName", relationship.equals("MOTHER") ? "  Maria  " : "  João  ",
                "surname", "  Silva  ",
                "cpf", "111.444.777-35",
                "phoneNumber", "+55 19 99888-7766",
                "email", "maria@example.com",
                "atLeast18", true
        ));
        payload.put("health", validHealthPayload());
        payload.put("declarations", validDeclarationsPayload());
        payload.put("signedOn", "2026-07-20");
        return payload;
    }

    private Map<String, Object> invalidConditionalPayload(String scenario) {
        Map<String, Object> payload = scenario.equals("minor missing school")
                ? validMinorParentPayload("MOTHER")
                : validAdultSelfFormPayload();
        switch (scenario) {
            case "minor missing school" -> payload.remove("schoolName");
            case "responsible adult not confirmed" -> payload.put(
                    "responsible",
                    Map.of("relationship", "SELF", "atLeast18", false)
            );
            case "relative missing complement" -> payload.put(
                    "responsible",
                    Map.of(
                            "relationship", "RELATIVE",
                            "firstName", "Maria",
                            "surname", "Silva",
                            "cpf", "11144477735",
                            "phoneNumber", "+5519998887766",
                            "atLeast18", true
                    )
            );
            case "YES health answer missing explanation" -> {
                Map<String, Object> health = validHealthPayload();
                health.put("medicalFollowUp", Map.of("answer", "YES"));
                payload.put("health", health);
            }
            case "NO health answer with contradictory explanation" -> {
                Map<String, Object> health = validHealthPayload();
                health.put("allergies", Map.of(
                        "answer", "NO",
                        "explanation", "Contradictory allergy details"
                ));
                payload.put("health", health);
            }
            case "incomplete parent snapshot" -> payload.put(
                    "father",
                    Map.of("cpf", "11144477735")
            );
            case "mandatory declaration refused" -> {
                Map<String, Object> declarations = validDeclarationsPayload();
                declarations.put("imageAndVoiceAuthorizationAccepted", false);
                payload.put("declarations", declarations);
            }
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
        return payload;
    }

    private Map<String, Object> validHealthPayload() {
        Map<String, Object> question = Map.of("answer", "NO");
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("medicalFollowUp", question);
        health.put("physicalActivityRestriction", question);
        health.put("medicineUse", question);
        health.put("allergies", question);
        health.put("convulsions", question);
        health.put("frequentFainting", question);
        health.put("heartCondition", question);
        health.put("otherHealthCondition", question);
        return health;
    }

    private Map<String, Object> validDeclarationsPayload() {
        Map<String, Object> declarations = new LinkedHashMap<>();
        declarations.put("signerRelationshipConfirmed", true);
        declarations.put("informationTruthConfirmed", true);
        declarations.put("healthInformationCurrentConfirmed", true);
        declarations.put("informationUseUnderstood", true);
        declarations.put("formReviewed", true);
        declarations.put("imageAndVoiceAuthorizationAccepted", true);
        return declarations;
    }

    private UUID readyAdultDraft(
            AuthSession caller,
            UUID oratorianoId,
            String firstName,
            String surname,
            String signedOn
    ) {
        UUID formId = draftId(createDraft(caller, oratorianoId, "DIRECT_SYSTEM_ENTRY"));
        Map<String, Object> payload = validAdultSelfFormPayload();
        payload.put("firstName", firstName);
        payload.put("surname", surname);
        payload.put("signedOn", signedOn);
        authenticatedJsonRequest(caller)
                .body(payload)
                .put(formPath(oratorianoId, formId))
                .then()
                .statusCode(200);
        SnapshotPdf snapshot = createSnapshotAndRender(caller, oratorianoId, formId);
        ExtractableResponse<Response> upload = replaceAttachments(
                caller,
                oratorianoId,
                formId,
                List.of(new TestAttachment("signed.pdf", "application/pdf", snapshot.bytes()))
        );
        assertThat(upload.statusCode()).isEqualTo(200);
        return formId;
    }

    private SnapshotPdf createSnapshotAndRender(
            AuthSession caller,
            UUID oratorianoId,
            UUID formId
    ) {
        ExtractableResponse<Response> snapshot = authenticatedJsonRequest(caller)
                .post(formPath(oratorianoId, formId) + "/print-snapshots")
                .then()
                .statusCode(201)
                .extract();
        UUID snapshotId = UUID.fromString(snapshot.path("id"));
        latestSnapshotIds.put(formId, snapshotId);
        int pageCount = snapshot.<Number>path("pageCount").intValue();
        Instant generatedAt = Instant.parse(snapshot.path("generatedAt"));
        String templateVersion = snapshot.path("templateVersion");
        byte[] pdf = authenticatedJsonRequest(caller)
                .accept(ContentType.BINARY)
                .get(
                        formPath(oratorianoId, formId)
                                + "/print-snapshots/{printSnapshotId}/pdf",
                        snapshotId
                )
                .then()
                .statusCode(200)
                .extract()
                .asByteArray();
        return new SnapshotPdf(snapshotId, generatedAt, templateVersion, pageCount, pdf);
    }

    private Map<String, Object> completionRequest(UUID formId, boolean overwriteNewerProfileValues) {
        UUID snapshotId = latestSnapshotIds.get(formId);
        if (snapshotId == null) {
            throw new AssertionError("No print snapshot was generated for form " + formId);
        }
        return completionRequestForSnapshot(snapshotId, overwriteNewerProfileValues);
    }

    private Map<String, Object> completionRequestForSnapshot(
            UUID snapshotId,
            boolean overwriteNewerProfileValues
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("printSnapshotId", snapshotId.toString());
        request.put("overwriteNewerProfileValues", overwriteNewerProfileValues);
        return request;
    }

    private static String pdfText(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static List<String> pdfPageTexts(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            List<String> pages = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document));
            }
            return pages;
        }
    }

    private static void assertPrintReadyTextLayout(byte[] bytes, int expectedPageCount)
            throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(expectedPageCount);
            PDFRenderer renderer = new PDFRenderer(document);
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                PDPage page = document.getPage(pageIndex);
                PositionCapturingStripper stripper = new PositionCapturingStripper();
                stripper.setStartPage(pageIndex + 1);
                stripper.setEndPage(pageIndex + 1);
                stripper.getText(document);

                float width = page.getCropBox().getWidth();
                float height = page.getCropBox().getHeight();
                assertThat(stripper.positions).isNotEmpty();
                assertThat(stripper.positions).allSatisfy(position -> {
                    assertThat(position.getXDirAdj()).isGreaterThanOrEqualTo(20.0f);
                    assertThat(position.getXDirAdj() + position.getWidthDirAdj())
                            .isLessThanOrEqualTo(width - 20.0f);
                    assertThat(position.getYDirAdj()).isGreaterThanOrEqualTo(20.0f);
                    assertThat(position.getYDirAdj() + position.getHeightDir())
                            .isLessThanOrEqualTo(height - 20.0f);
                });

                BufferedImage rendered = renderer.renderImageWithDPI(pageIndex, 96);
                assertThat(nonWhitePixelCount(rendered))
                        .as("rendered page %s must contain visible form content", pageIndex + 1)
                        .isGreaterThan(100);
            }
        }
    }

    private static long nonWhitePixelCount(BufferedImage image) {
        long count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00FFFFFF) != 0x00FFFFFF) {
                    count++;
                }
            }
        }
        return count;
    }

    private static final class PositionCapturingStripper extends PDFTextStripper {
        private final List<TextPosition> positions = new ArrayList<>();

        private PositionCapturingStripper() throws IOException {
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            positions.add(text);
            super.processTextPosition(text);
        }
    }

    private static void assertContainsDeclarationConcepts(String text) {
        assertContainsFieldConcept(text, "signer relationship", "relação do signatário");
        assertContainsFieldConcept(
                text,
                "information is true",
                "information truth",
                "informações são verdadeiras"
        );
        assertContainsFieldConcept(
                text,
                "health information",
                "informações de saúde"
        );
        assertContainsFieldConcept(
                text,
                "information use",
                "use of information",
                "uso das informações"
        );
        assertContainsFieldConcept(text, "form reviewed", "reviewed form", "formulário revisado");
        assertContainsFieldConcept(text, "image and voice", "image-and-voice", "imagem e voz");
    }

    private static void assertContainsFieldConcept(String text, String... acceptedPhrases) {
        String normalized = text.toLowerCase();
        assertThat(normalized).containsAnyOf(
                Arrays.stream(acceptedPhrases)
                        .map(String::toLowerCase)
                        .toArray(String[]::new)
        );
    }

    private static int countOccurrencesIgnoringCase(String text, String token) {
        String normalizedText = text.toLowerCase();
        String normalizedToken = token.toLowerCase();
        int count = 0;
        int offset = 0;
        while ((offset = normalizedText.indexOf(normalizedToken, offset)) >= 0) {
            count++;
            offset += normalizedToken.length();
        }
        return count;
    }

    private void awaitMutationLockWaiters(int expectedCount) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_stat_activity "
                            + "WHERE datname = current_database() AND wait_event_type = 'Lock' "
                            + "AND (query ILIKE '%oratoriano_additional_forms%' "
                            + "OR query ILIKE '%oratorianos%')",
                    Long.class
            );
            if (count != null && count >= expectedCount) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for serialized Oratoriano mutations.");
    }

    private ExtractableResponse<Response> replaceAttachments(
            AuthSession caller,
            UUID oratorianoId,
            UUID formId,
            List<TestAttachment> attachments
    ) {
        RequestSpecification request = authenticatedJsonRequest(caller)
                .contentType(ContentType.MULTIPART)
                .accept(ContentType.JSON);
        for (TestAttachment attachment : attachments) {
            request.multiPart(
                    "files",
                    attachment.filename(),
                    attachment.bytes(),
                    attachment.mimeType()
            );
        }
        return request.put(formPath(oratorianoId, formId) + "/signed-attachments")
                .then()
                .extract();
    }

    private List<UUID> activeAttachmentIds(UUID formId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM oratoriano_form_attachments "
                        + "WHERE form_id = ? AND deleted_at IS NULL ORDER BY page_order",
                UUID.class,
                formId
        );
    }

    private static List<TestAttachment> imagePages(int count, int byteLength) {
        List<TestAttachment> pages = new ArrayList<>();
        for (int page = 1; page <= count; page++) {
            boolean jpeg = page % 2 == 1;
            pages.add(new TestAttachment(
                    "page-" + page + (jpeg ? ".jpg" : ".png"),
                    jpeg ? "image/jpeg" : "image/png",
                    jpeg ? jpegBytes(byteLength) : pngBytes(byteLength)
            ));
        }
        return pages;
    }

    private static byte[] pdfBytes(int byteLength) {
        return paddedToLength(pdfWithPages(1), byteLength);
    }

    private static byte[] pdfWithPages(int pageCount) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int page = 0; page < pageCount; page++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create a valid PDF fixture.", exception);
        }
    }

    private static byte[] rasterizedPdfScan(byte[] source) {
        try (PDDocument original = Loader.loadPDF(source);
             PDDocument scanned = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFRenderer renderer = new PDFRenderer(original);
            for (int pageIndex = 0; pageIndex < original.getNumberOfPages(); pageIndex++) {
                PDPage originalPage = original.getPage(pageIndex);
                PDPage scannedPage = new PDPage(originalPage.getMediaBox());
                scanned.addPage(scannedPage);
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 144);
                try (PDPageContentStream content = new PDPageContentStream(scanned, scannedPage)) {
                    content.drawImage(
                            LosslessFactory.createFromImage(scanned, image),
                            0,
                            0,
                            scannedPage.getMediaBox().getWidth(),
                            scannedPage.getMediaBox().getHeight()
                    );
                }
            }
            scanned.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to rasterize a PDF scan fixture.", exception);
        }
    }

    private static byte[] pngBytes(int byteLength) {
        return paddedToLength(imageBytes("png"), byteLength);
    }

    private static byte[] jpegBytes(int byteLength) {
        return paddedToLength(imageBytes("jpeg"), byteLength);
    }

    private static byte[] imageBytes(String format) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("No ImageIO writer for " + format + ".");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create a valid image fixture.", exception);
        }
    }

    private static byte[] paddedToLength(byte[] validContent, int byteLength) {
        if (byteLength <= validContent.length) {
            return validContent;
        }
        return Arrays.copyOf(validContent, byteLength);
    }

    private static byte[] signedBytes(byte[] signature, int byteLength) {
        if (byteLength < signature.length) {
            throw new IllegalArgumentException("Fixture byte length must fit its MIME signature.");
        }
        return Arrays.copyOf(signature, byteLength);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static Stream<Arguments> malformedMagicPrefixAttachmentCases() {
        return Stream.of(
                Arguments.of(
                        "PDF prefix without a parseable PDF",
                        new TestAttachment(
                                "malformed.pdf",
                                "application/pdf",
                                signedBytes("%PDF-".getBytes(StandardCharsets.US_ASCII), 64)
                        )
                ),
                Arguments.of(
                        "PNG prefix without a parseable PNG",
                        new TestAttachment(
                                "malformed.png",
                                "image/png",
                                signedBytes(
                                        new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'},
                                        64
                                )
                        )
                ),
                Arguments.of(
                        "JPEG prefix without a parseable JPEG",
                        new TestAttachment(
                                "malformed.jpg",
                                "image/jpeg",
                                signedBytes(
                                        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff},
                                        64
                                )
                        )
                )
        );
    }

    private record SnapshotPdf(
            UUID id,
            Instant generatedAt,
            String templateVersion,
            int pageCount,
            byte[] bytes
    ) {
    }

    private record TestAttachment(String filename, String mimeType, byte[] bytes) {
    }

}
