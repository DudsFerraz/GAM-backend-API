package br.org.gam.api.oratoriano.application.useCases;

import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.AddressDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.AccountReferenceRDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.AttachmentRDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.CreateFormDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.DeclarationsDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormDraftDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormHistoryRDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormOrigin;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormRDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormStatus;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.HealthDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.HealthAnswer;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.HealthQuestionDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.ParentDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.PrintMode;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.PrintSnapshotRDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.ResponsibleDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.ResponsibleRelationship;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.oratoriano.persistence.OratorianoRepository;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import br.org.gam.api.shared.exception.ConflictException;
import br.org.gam.api.shared.exception.InvalidCommandException;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.shared.domain.GamCPF;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.domain.GamRG;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import br.org.gam.api.shared.validation.RequiredReason;
import br.org.gam.api.shared.web.PagedResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OratorianoForms {
    private static final String TEMPLATE_VERSION = "oratoriano-additional-form-v1";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final long MEBIBYTE = 1024L * 1024L;
    private static final float PDF_MARGIN = 32;
    private static final float PDF_FONT_SIZE = 9;
    private static final float PDF_LEADING = 12;
    private static final int PDF_HEADER_LINES = 4;

    private final OratorianoRepository oratorianoRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditorAware<UUID> auditorAware;
    private final ActivityEvents activityEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OratorianoForms(
            OratorianoRepository oratorianoRepository,
            JdbcTemplate jdbcTemplate,
            AuditorAware<UUID> auditorAware,
            ActivityEvents activityEvents,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.oratorianoRepository = oratorianoRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditorAware = auditorAware;
        this.activityEvents = activityEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public FormRDTO create(UUID oratorianoId, CreateFormDTO dto) {
        oratorianoRepository.findActiveByIdForUpdate(oratorianoId)
                .orElseThrow(() -> NotFoundException.resource("Oratoriano", oratorianoId));
        if (dto.origin() == null) {
            throw InvalidCommandException.reason("Additional-form origin is required.");
        }
        Integer nextVersion = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) + 1 FROM oratoriano_additional_forms WHERE oratoriano_id = ?",
                Integer.class,
                oratorianoId
        );
        UUID id = UUIDGenerator.generateUUIDV7();
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        Instant now = clock.instant();
        jdbcTemplate.update(
                "INSERT INTO oratoriano_additional_forms "
                        + "(id, oratoriano_id, version, status, origin, draft_revision, draft_data, "
                        + "created_at, created_by, updated_at, updated_by) "
                        + "VALUES (?, ?, ?, 'DRAFT', ?::oratoriano_form_origin_enum, 1, '{}'::jsonb, ?, ?, ?, ?)",
                id,
                oratorianoId,
                nextVersion,
                dto.origin().name(),
                Timestamp.from(now),
                actor,
                Timestamp.from(now),
                actor
        );
        activity(
                ActivityAction.ORATORIANO_FORM_DRAFT_CREATED,
                id,
                null,
                "Oratoriano additional-form draft created",
                Map.of("oratorianoId", oratorianoId, "origin", dto.origin().name())
        );
        return getRow(oratorianoId, id);
    }

    @Transactional
    public FormRDTO replaceDraft(UUID oratorianoId, UUID formId, FormDraftDTO dto) {
        requireActiveOratorianoForUpdate(oratorianoId);
        FormRow row = requiredForUpdate(oratorianoId, formId);
        assertDraft(row);
        Map<String, Object> data = draftData(dto);
        if (Objects.equals(row.data(), data)) {
            return row.toRDTO();
        }
        long revision = row.draftRevision() + 1;
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        jdbcTemplate.update(
                "UPDATE oratoriano_additional_forms "
                        + "SET draft_data = ?::jsonb, draft_revision = ?, signed_on = ?, updated_at = ?, updated_by = ? "
                        + "WHERE id = ?",
                json(data),
                revision,
                dto.signedOn(),
                Timestamp.from(clock.instant()),
                actor,
                formId
        );
        activity(
                ActivityAction.ORATORIANO_FORM_DRAFT_UPDATED,
                formId,
                null,
                "Oratoriano additional-form draft updated",
                Map.of("oratorianoId", oratorianoId, "draftRevision", revision)
        );
        return getRow(oratorianoId, formId);
    }

    @Transactional
    public void complete(
            UUID oratorianoId,
            UUID formId,
            UUID printSnapshotId,
            boolean overwriteNewerProfileValues
    ) {
        OratorianoEntity oratoriano = oratorianoRepository.findActiveByIdForUpdate(oratorianoId)
                .orElseThrow(() -> NotFoundException.resource("Oratoriano", oratorianoId));
        FormRow row = requiredForUpdate(oratorianoId, formId);
        assertDraft(row);
        CompletionData completion = completionData(row);
        List<AttachmentRow> attachments = activeAttachments(formId);
        validateAttachmentCollection(attachments);
        LatestSnapshot snapshot = jdbcTemplate.query(
                "SELECT id, draft_revision, page_count FROM oratoriano_form_print_snapshots "
                        + "WHERE form_id = ? AND id = ? AND deleted_at IS NULL",
                rs -> rs.next()
                        ? new LatestSnapshot(
                                rs.getObject("id", UUID.class),
                                rs.getLong("draft_revision"),
                                rs.getInt("page_count")
                        )
                        : null,
                formId,
                printSnapshotId
        );
        UUID latestSnapshotId = row.origin() == FormOrigin.DIRECT_SYSTEM_ENTRY
                ? jdbcTemplate.query(
                        "SELECT id FROM oratoriano_form_print_snapshots "
                                + "WHERE form_id = ? AND deleted_at IS NULL "
                                + "ORDER BY generated_at DESC, id DESC LIMIT 1",
                        rs -> rs.next() ? rs.getObject("id", UUID.class) : null,
                        formId
                )
                : null;
        if (snapshot == null
                || attachmentPageCount(attachments) != snapshot.pageCount()
                || !attachmentCorrespondsToSelectedSnapshot(attachments, snapshot.id())
                || row.origin() == FormOrigin.DIRECT_SYSTEM_ENTRY
                && (snapshot.draftRevision() != row.draftRevision()
                || !snapshot.id().equals(latestSnapshotId))) {
            throw incomplete();
        }

        assertProfileReplacementAllowed(
                oratoriano,
                row,
                completion,
                overwriteNewerProfileValues
        );
        oratoriano.setName(completion.name());
        oratoriano.setNameKey(OratorianoRecords.humanEquivalentNameKey(completion.name()));
        oratoriano.setBirthDate(completion.birthDate());
        oratoriano.setNameSourceFormId(formId);
        oratoriano.setNameSourceSignedOn(row.signedOn());
        oratoriano.setNameManualUpdatedAt(null);
        oratoriano.setBirthDateSourceFormId(formId);
        oratoriano.setBirthDateSourceSignedOn(row.signedOn());
        oratoriano.setBirthDateManualUpdatedAt(null);
        if (completion.phoneNumber() != null) {
            oratoriano.setPhoneNumber(completion.phoneNumber());
            oratoriano.setPhoneSourceFormId(formId);
            oratoriano.setPhoneSourceSignedOn(row.signedOn());
            oratoriano.setPhoneManualUpdatedAt(null);
        }
        try {
            oratorianoRepository.saveAndFlush(oratoriano);
        } catch (DataIntegrityViolationException exception) {
            throw ConflictException.resource(
                    "ORATORIANO_NAME_RESERVED",
                    "Oratoriano",
                    OratorianoRecords.humanEquivalentNameKey(completion.name()),
                    "Another Oratoriano reserves the completed form name."
            );
        }

        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        Timestamp now = Timestamp.from(clock.instant());
        jdbcTemplate.update(
                "UPDATE oratoriano_additional_forms "
                        + "SET status = 'SUPERSEDED', updated_at = ?, updated_by = ? "
                        + "WHERE oratoriano_id = ? AND id <> ? AND status = 'COMPLETED' AND deleted_at IS NULL",
                now,
                actor,
                oratorianoId,
                formId
        );
        jdbcTemplate.update(
                "UPDATE oratoriano_additional_forms "
                        + "SET status = 'COMPLETED', completed_at = ?, completed_by = ?, "
                        + "draft_data = ?::jsonb, updated_at = ?, updated_by = ? WHERE id = ?",
                now,
                actor,
                json(completion.canonicalData()),
                now,
                actor,
                formId
        );
        activity(
                ActivityAction.ORATORIANO_FORM_COMPLETED,
                formId,
                null,
                "Oratoriano additional form completed",
                Map.of("oratorianoId", oratorianoId)
        );
    }

    @Transactional
    public List<AttachmentRDTO> replaceAttachments(
            UUID oratorianoId,
            UUID formId,
            List<MultipartFile> files
    ) {
        requireActiveOratorianoForUpdate(oratorianoId);
        FormRow row = requiredForUpdate(oratorianoId, formId);
        assertDraft(row);
        List<NewAttachment> replacements = attachmentUploads(files);
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        Timestamp now = Timestamp.from(clock.instant());
        jdbcTemplate.update(
                "UPDATE oratoriano_form_attachments SET deleted_at = ?, deleted_by = ?, "
                        + "updated_at = ?, updated_by = ? "
                        + "WHERE form_id = ? AND deleted_at IS NULL",
                now,
                actor,
                now,
                actor,
                formId
        );
        List<AttachmentRDTO> result = new ArrayList<>();
        for (int index = 0; index < replacements.size(); index++) {
            NewAttachment replacement = replacements.get(index);
            UUID attachmentId = UUIDGenerator.generateUUIDV7();
            int pageOrder = index + 1;
            jdbcTemplate.update(
                    "INSERT INTO oratoriano_form_attachments "
                            + "(id, form_id, original_filename, verified_mime_type, byte_length, page_order, page_count, "
                            + "sha256, bytes, created_at, created_by, updated_at, updated_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    attachmentId,
                    formId,
                    replacement.originalFilename(),
                    replacement.verifiedMimeType(),
                    replacement.bytes().length,
                    pageOrder,
                    replacement.pageCount(),
                    sha256(replacement.bytes()),
                    replacement.bytes(),
                    now,
                    actor,
                    now,
                    actor
            );
            result.add(new AttachmentRDTO(
                    attachmentId,
                    replacement.originalFilename(),
                    replacement.verifiedMimeType(),
                    replacement.bytes().length,
                    pageOrder
            ));
        }
        activity(
                ActivityAction.ORATORIANO_FORM_ATTACHMENTS_REPLACED,
                formId,
                null,
                "Oratoriano form signed attachments replaced",
                Map.of(
                        "oratorianoId", oratorianoId,
                        "attachmentCount", result.size()
                )
        );
        return List.copyOf(result);
    }

    @Transactional
    public AttachmentDownload downloadAttachment(UUID oratorianoId, UUID formId, UUID attachmentId) {
        required(oratorianoId, formId);
        AttachmentDownload attachment = jdbcTemplate.query(
                "SELECT original_filename, verified_mime_type, bytes "
                        + "FROM oratoriano_form_attachments "
                        + "WHERE id = ? AND form_id = ? AND deleted_at IS NULL",
                rs -> rs.next()
                        ? new AttachmentDownload(
                                rs.getString("original_filename"),
                                rs.getString("verified_mime_type"),
                                rs.getBytes("bytes")
                        )
                        : null,
                attachmentId,
                formId
        );
        if (attachment == null) {
            throw NotFoundException.resource("OratorianoFormAttachment", attachmentId);
        }
        activity(
                ActivityAction.ORATORIANO_FORM_ATTACHMENT_DOWNLOADED,
                formId,
                null,
                "Sensitive Oratoriano form attachment downloaded",
                Map.of(
                        "oratorianoId", oratorianoId,
                        "attachmentId", attachmentId
                )
        );
        return attachment;
    }

    @Transactional
    public PrintSnapshotRDTO createPrintSnapshot(UUID oratorianoId, UUID formId) {
        requireActiveOratorianoForUpdate(oratorianoId);
        FormRow row = requiredForUpdate(oratorianoId, formId);
        assertDraft(row);
        UUID id = UUIDGenerator.generateUUIDV7();
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        Instant now = clock.instant();
        PrintMode mode = row.origin() == FormOrigin.PAPER_TRANSCRIPTION
                ? PrintMode.IDENTIFIED_BLANK
                : PrintMode.PREFILLED;
        String captured = json(row.data());
        String fingerprint = sha256(captured);
        int pageCount = printablePageCount(
                oratorianoId,
                formId,
                new PrintPdfData(id, mode, now, 0, row.data())
        );
        jdbcTemplate.update(
                "INSERT INTO oratoriano_form_print_snapshots "
                        + "(id, form_id, draft_revision, mode, generated_at, template_version, page_count, "
                        + "captured_data, fingerprint, created_at, created_by, updated_at, updated_by) "
                        + "VALUES (?, ?, ?, ?::oratoriano_form_print_mode_enum, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)",
                id,
                formId,
                row.draftRevision(),
                mode.name(),
                Timestamp.from(now),
                TEMPLATE_VERSION,
                pageCount,
                captured,
                fingerprint,
                Timestamp.from(now),
                actor,
                Timestamp.from(now),
                actor
        );
        activity(
                ActivityAction.ORATORIANO_FORM_PRINT_SNAPSHOT_CREATED,
                formId,
                null,
                "Oratoriano form print snapshot created",
                Map.of(
                        "oratorianoId", oratorianoId,
                        "printSnapshotId", id
                )
        );
        return new PrintSnapshotRDTO(
                id,
                formId,
                row.draftRevision(),
                mode,
                now,
                TEMPLATE_VERSION,
                pageCount,
                fingerprint
        );
    }

    @Transactional
    public byte[] renderPdf(UUID oratorianoId, UUID formId, UUID snapshotId) {
        required(oratorianoId, formId);
        PrintPdfData snapshot = jdbcTemplate.query(
                "SELECT id, mode::text, generated_at, page_count, captured_data::text "
                        + "FROM oratoriano_form_print_snapshots "
                        + "WHERE id = ? AND form_id = ? AND deleted_at IS NULL",
                rs -> rs.next()
                        ? new PrintPdfData(
                                rs.getObject("id", UUID.class),
                                PrintMode.valueOf(rs.getString("mode")),
                                rs.getTimestamp("generated_at").toInstant(),
                                rs.getInt("page_count"),
                                map(rs.getString("captured_data"))
                        )
                        : null,
                snapshotId,
                formId
        );
        if (snapshot == null) {
            throw NotFoundException.resource("OratorianoFormPrintSnapshot", snapshotId);
        }
        byte[] bytes = printablePdf(oratorianoId, formId, snapshot);
        activity(
                ActivityAction.ORATORIANO_FORM_PDF_RENDERED,
                formId,
                null,
                "Oratoriano form PDF rendered",
                Map.of(
                        "oratorianoId", oratorianoId,
                        "printSnapshotId", snapshotId
                )
        );
        return bytes;
    }

    @Transactional
    public void deleteDraft(UUID oratorianoId, UUID formId, String rawReason) {
        String reason = RequiredReason.normalize(rawReason, "Draft deletion requires an audit reason.");
        oratorianoRepository.findActiveByIdForUpdate(oratorianoId)
                .orElseThrow(() -> NotFoundException.resource("Oratoriano", oratorianoId));
        FormRow row = requiredForUpdate(oratorianoId, formId);
        assertDraft(row);
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        Timestamp now = Timestamp.from(clock.instant());
        jdbcTemplate.update(
                "UPDATE oratoriano_form_attachments SET deleted_at = ?, deleted_by = ? "
                        + "WHERE form_id = ? AND deleted_at IS NULL",
                now,
                actor,
                formId
        );
        jdbcTemplate.update(
                "UPDATE oratoriano_form_print_snapshots SET deleted_at = ?, deleted_by = ? "
                        + "WHERE form_id = ? AND deleted_at IS NULL",
                now,
                actor,
                formId
        );
        jdbcTemplate.update(
                "UPDATE oratoriano_additional_forms SET deleted_at = ?, deleted_by = ? "
                        + "WHERE id = ? AND deleted_at IS NULL",
                now,
                actor,
                formId
        );
        activity(
                ActivityAction.ORATORIANO_FORM_DRAFT_DELETED,
                formId,
                reason,
                "Oratoriano additional-form draft deleted",
                Map.of("oratorianoId", oratorianoId)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<FormHistoryRDTO> history(UUID oratorianoId, int page, int size) {
        oratorianoRepository.findById(oratorianoId)
                .orElseThrow(() -> NotFoundException.resource("Oratoriano", oratorianoId));
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.max(1, Math.min(size, 100));
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oratoriano_additional_forms "
                        + "WHERE oratoriano_id = ? AND deleted_at IS NULL",
                Long.class,
                oratorianoId
        );
        List<FormHistoryRDTO> items = jdbcTemplate.query(
                "SELECT f.id, f.version, f.status::text, f.origin::text, f.signed_on, "
                        + "f.created_at, f.created_by, f.completed_at, f.completed_by, "
                        + "f.revoked_at, f.revoked_by, "
                        + "EXISTS (SELECT 1 FROM oratoriano_form_attachments a "
                        + "WHERE a.form_id = f.id AND a.deleted_at IS NULL) AS attachment_exists, "
                        + "(SELECT COALESCE(SUM(a.page_count), 0) FROM oratoriano_form_attachments a "
                        + "WHERE a.form_id = f.id AND a.deleted_at IS NULL) AS attachment_page_count "
                        + "FROM oratoriano_additional_forms f "
                        + "WHERE f.oratoriano_id = ? AND f.deleted_at IS NULL "
                        + "ORDER BY f.version DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new FormHistoryRDTO(
                        rs.getObject("id", UUID.class),
                        rs.getInt("version"),
                        FormStatus.valueOf(rs.getString("status")),
                        FormOrigin.valueOf(rs.getString("origin")),
                        rs.getObject("signed_on", LocalDate.class),
                        rs.getTimestamp("created_at").toInstant(),
                        accountReference(rs.getObject("created_by", UUID.class)),
                        instant(rs.getTimestamp("completed_at")),
                        accountReference(rs.getObject("completed_by", UUID.class)),
                        instant(rs.getTimestamp("revoked_at")),
                        accountReference(rs.getObject("revoked_by", UUID.class)),
                        rs.getBoolean("attachment_exists"),
                        rs.getInt("attachment_page_count")
                ),
                oratorianoId,
                boundedSize,
                boundedPage * boundedSize
        );
        long totalElements = total == null ? 0 : total;
        int totalPages = totalElements == 0
                ? 0
                : (int) Math.ceil((double) totalElements / boundedSize);
        return new PagedResponse<>(
                items,
                boundedPage,
                boundedSize,
                totalElements,
                totalPages,
                boundedPage == 0,
                totalPages == 0 || boundedPage >= totalPages - 1
        );
    }

    @Transactional
    public FormRDTO detail(UUID oratorianoId, UUID formId) {
        FormRow row = required(oratorianoId, formId);
        activity(
                ActivityAction.ORATORIANO_FORM_DETAIL_READ,
                formId,
                null,
                "Sensitive Oratoriano form detail read",
                Map.of("oratorianoId", oratorianoId)
        );
        return row.toRDTO();
    }

    @Transactional
    public void revoke(UUID oratorianoId, UUID formId, String rawReason) {
        String reason = RequiredReason.normalize(rawReason, "Form revocation requires an audit reason.");
        requireActiveOratorianoForUpdate(oratorianoId);
        FormRow row = requiredForUpdate(oratorianoId, formId);
        if (row.status() != FormStatus.COMPLETED) {
            throw ConflictException.resource(
                    "ORATORIANO_FORM_NOT_CURRENT",
                    "OratorianoForm",
                    formId,
                    "Only the current completed form may be revoked."
            );
        }
        UUID actor = auditorAware.getCurrentAuditor().orElse(null);
        Timestamp now = Timestamp.from(clock.instant());
        jdbcTemplate.update(
                "UPDATE oratoriano_additional_forms "
                        + "SET status = 'REVOKED', revoked_at = ?, revoked_by = ?, updated_at = ?, updated_by = ? "
                        + "WHERE id = ?",
                now,
                actor,
                now,
                actor,
                formId
        );
        activity(
                ActivityAction.ORATORIANO_FORM_REVOKED,
                formId,
                reason,
                "Current Oratoriano form revoked",
                Map.of("oratorianoId", oratorianoId)
        );
    }

    private FormRDTO getRow(UUID oratorianoId, UUID formId) {
        return required(oratorianoId, formId).toRDTO();
    }

    private OratorianoEntity requireActiveOratorianoForUpdate(UUID oratorianoId) {
        return oratorianoRepository.findActiveByIdForUpdate(oratorianoId)
                .orElseThrow(() -> NotFoundException.resource("Oratoriano", oratorianoId));
    }

    private FormRow required(UUID oratorianoId, UUID formId) {
        List<FormRow> rows = jdbcTemplate.query(
                "SELECT id, oratoriano_id, version, status::text, origin::text, draft_revision, "
                        + "draft_data::text, signed_on, created_by, created_at "
                        + "FROM oratoriano_additional_forms "
                        + "WHERE id = ? AND oratoriano_id = ? AND deleted_at IS NULL",
                (rs, rowNum) -> new FormRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("oratoriano_id", UUID.class),
                        rs.getInt("version"),
                        FormStatus.valueOf(rs.getString("status")),
                        FormOrigin.valueOf(rs.getString("origin")),
                        rs.getLong("draft_revision"),
                        map(rs.getString("draft_data")),
                        rs.getObject("signed_on", LocalDate.class),
                        rs.getObject("created_by", UUID.class),
                        rs.getTimestamp("created_at").toInstant()
                ),
                formId,
                oratorianoId
        );
        if (rows.isEmpty()) {
            throw NotFoundException.resource("OratorianoForm", formId);
        }
        return rows.getFirst();
    }

    private FormRow requiredForUpdate(UUID oratorianoId, UUID formId) {
        List<FormRow> rows = jdbcTemplate.query(
                "SELECT id, oratoriano_id, version, status::text, origin::text, draft_revision, "
                        + "draft_data::text, signed_on, created_by, created_at "
                        + "FROM oratoriano_additional_forms "
                        + "WHERE id = ? AND oratoriano_id = ? AND deleted_at IS NULL FOR UPDATE",
                (rs, rowNum) -> new FormRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("oratoriano_id", UUID.class),
                        rs.getInt("version"),
                        FormStatus.valueOf(rs.getString("status")),
                        FormOrigin.valueOf(rs.getString("origin")),
                        rs.getLong("draft_revision"),
                        map(rs.getString("draft_data")),
                        rs.getObject("signed_on", LocalDate.class),
                        rs.getObject("created_by", UUID.class),
                        rs.getTimestamp("created_at").toInstant()
                ),
                formId,
                oratorianoId
        );
        if (rows.isEmpty()) {
            throw NotFoundException.resource("OratorianoForm", formId);
        }
        return rows.getFirst();
    }

    private void assertDraft(FormRow row) {
        if (row.status() != FormStatus.DRAFT) {
            throw ConflictException.resource(
                    "ORATORIANO_FORM_IMMUTABLE",
                    "OratorianoForm",
                    row.id(),
                    "Only a draft form may be edited or deleted."
            );
        }
    }

    private Map<String, Object> draftData(FormDraftDTO dto) {
        if (dto == null || isEmpty(dto)) {
            return Map.of();
        }
        return objectMapper.convertValue(dto, new TypeReference<>() {
        });
    }

    private boolean isEmpty(FormDraftDTO dto) {
        return dto.firstName() == null
                && dto.surname() == null
                && dto.birthDate() == null
                && dto.cpf() == null
                && dto.rg() == null
                && dto.address() == null
                && dto.phoneNumber() == null
                && dto.schoolName() == null
                && dto.schoolGrade() == null
                && dto.responsible() == null
                && dto.father() == null
                && dto.mother() == null
                && dto.health() == null
                && dto.declarations() == null
                && dto.signedOn() == null;
    }

    private CompletionData completionData(FormRow row) {
        if (row.data().isEmpty() || row.signedOn() == null) {
            throw incomplete();
        }
        try {
            FormDraftDTO draft = objectMapper.convertValue(row.data(), FormDraftDTO.class);
            String firstName = requiredText(draft.firstName(), "firstName", 32);
            String surname = requiredText(draft.surname(), "surname", 64);
            GamName name = new GamName(firstName, surname);
            LocalDate birthDate = Objects.requireNonNull(draft.birthDate(), "birthDate");
            LocalDate signedOn = Objects.requireNonNull(draft.signedOn(), "signedOn");
            if (!signedOn.equals(row.signedOn())
                    || birthDate.isAfter(signedOn)
                    || signedOn.isAfter(LocalDate.now(clock.withZone(SAO_PAULO)))) {
                throw new IllegalArgumentException("invalid form dates");
            }

            String cpf = new GamCPF(requiredText(draft.cpf(), "cpf", 14)).value();
            String rg = draft.rg() == null
                    ? null
                    : new GamRG(optionalText(draft.rg(), "rg", 20)).value();
            AddressDTO address = canonicalAddress(draft.address());

            boolean adult = Period.between(birthDate, signedOn).getYears() >= 18;
            GamPhoneNumber personalPhone = draft.phoneNumber() == null
                    ? null
                    : GamPhoneNumber.fromString(draft.phoneNumber());
            ResponsibleDTO responsible = canonicalResponsible(
                    draft.responsible(),
                    adult,
                    name,
                    cpf,
                    personalPhone
            );
            String schoolName;
            String schoolGrade;
            if (!adult) {
                schoolName = requiredText(draft.schoolName(), "schoolName", 255);
                schoolGrade = requiredText(draft.schoolGrade(), "schoolGrade", 100);
            } else {
                schoolName = optionalText(draft.schoolName(), "schoolName", 255);
                schoolGrade = optionalText(draft.schoolGrade(), "schoolGrade", 100);
            }
            ParentDTO father = canonicalParent(draft.father(), "father");
            ParentDTO mother = canonicalParent(draft.mother(), "mother");
            if (responsible.relationship() == ResponsibleRelationship.MOTHER) {
                mother = new ParentDTO(
                        responsible.firstName(),
                        responsible.surname(),
                        responsible.cpf()
                );
            } else if (responsible.relationship() == ResponsibleRelationship.FATHER) {
                father = new ParentDTO(
                        responsible.firstName(),
                        responsible.surname(),
                        responsible.cpf()
                );
            }
            HealthDTO health = canonicalHealth(draft.health());
            validateDeclarations(draft.declarations());

            if (draft.responsible().relationship() == ResponsibleRelationship.SELF && personalPhone == null) {
                throw new IllegalArgumentException("self-responsible adult requires a phone");
            }
            FormDraftDTO canonical = new FormDraftDTO(
                    name.firstName(),
                    name.surname(),
                    birthDate,
                    cpf,
                    rg,
                    address,
                    personalPhone == null ? null : personalPhone.value(),
                    schoolName,
                    schoolGrade,
                    responsible,
                    father,
                    mother,
                    health,
                    draft.declarations(),
                    signedOn
            );
            return new CompletionData(
                    name,
                    birthDate,
                    personalPhone,
                    objectMapper.convertValue(canonical, new TypeReference<>() {
                    })
            );
        } catch (RuntimeException exception) {
            if (exception instanceof InvalidCommandException) {
                throw exception;
            }
            throw incomplete();
        }
    }

    private AddressDTO canonicalAddress(AddressDTO address) {
        if (address == null) {
            throw new IllegalArgumentException("address is required");
        }
        String addressLine = requiredText(address.addressLine(), "addressLine", 255);
        String addressNumber = requiredText(address.addressNumber(), "addressNumber", 100);
        String neighborhood = requiredText(address.neighborhood(), "neighborhood", 255);
        String city = requiredText(address.city(), "city", 255);
        String cep = requiredText(address.cep(), "cep", 9);
        if (!cep.matches("[0-9]{8}|[0-9]{5}-[0-9]{3}")) {
            throw new IllegalArgumentException("invalid CEP");
        }
        return new AddressDTO(
                addressLine,
                addressNumber,
                neighborhood,
                cep.replace("-", ""),
                city
        );
    }

    private ResponsibleDTO canonicalResponsible(
            ResponsibleDTO responsible,
            boolean adult,
            GamName oratorianoName,
            String oratorianoCpf,
            GamPhoneNumber personalPhone
    ) {
        if (responsible == null
                || responsible.relationship() == null
                || !Boolean.TRUE.equals(responsible.atLeast18())) {
            throw new IllegalArgumentException("responsible adult is required");
        }
        ResponsibleRelationship relationship = responsible.relationship();
        String complement = optionalText(
                responsible.relationshipComplement(),
                "relationshipComplement",
                100
        );
        if (relationship == ResponsibleRelationship.SELF) {
            if (!adult || complement != null || personalPhone == null) {
                throw new IllegalArgumentException("invalid self-responsible form");
            }
            String email = responsible.email() == null
                    ? null
                    : new GamEmail(optionalText(
                            responsible.email(),
                            "responsible.email",
                            320
                    )).value();
            return new ResponsibleDTO(
                    ResponsibleRelationship.SELF,
                    null,
                    oratorianoName.firstName(),
                    oratorianoName.surname(),
                    new GamCPF(oratorianoCpf).value(),
                    personalPhone.value(),
                    email,
                    true
            );
        }
        if ((relationship == ResponsibleRelationship.RELATIVE
                || relationship == ResponsibleRelationship.REFERENCE_ADULT) && complement == null) {
            throw new IllegalArgumentException("relationship complement is required");
        }
        if (relationship != ResponsibleRelationship.RELATIVE
                && relationship != ResponsibleRelationship.REFERENCE_ADULT
                && complement != null) {
            throw new IllegalArgumentException("relationship complement is not accepted");
        }
        GamName name = new GamName(
                requiredText(responsible.firstName(), "responsible.firstName", 32),
                requiredText(responsible.surname(), "responsible.surname", 64)
        );
        String cpf = new GamCPF(requiredText(responsible.cpf(), "responsible.cpf", 14)).value();
        GamPhoneNumber phone = GamPhoneNumber.fromString(requiredText(
                responsible.phoneNumber(),
                "responsible.phoneNumber",
                32
        ));
        String email = null;
        if (responsible.email() != null) {
            email = new GamEmail(optionalText(
                    responsible.email(),
                    "responsible.email",
                    320
            )).value();
        }
        return new ResponsibleDTO(
                relationship,
                complement,
                name.firstName(),
                name.surname(),
                cpf,
                phone.value(),
                email,
                true
        );
    }

    private ParentDTO canonicalParent(ParentDTO parent, String field) {
        if (parent == null) {
            return null;
        }
        GamName name = new GamName(
                requiredText(parent.firstName(), field + ".firstName", 32),
                requiredText(parent.surname(), field + ".surname", 64)
        );
        return new ParentDTO(
                name.firstName(),
                name.surname(),
                new GamCPF(requiredText(parent.cpf(), field + ".cpf", 14)).value()
        );
    }

    private HealthDTO canonicalHealth(HealthDTO health) {
        if (health == null) {
            throw new IllegalArgumentException("health answers are required");
        }
        return new HealthDTO(
                canonicalHealthQuestion(health.medicalFollowUp(), "medicalFollowUp", false),
                canonicalHealthQuestion(
                        health.physicalActivityRestriction(),
                        "physicalActivityRestriction",
                        false
                ),
                canonicalHealthQuestion(health.medicineUse(), "medicineUse", true),
                canonicalHealthQuestion(health.allergies(), "allergies", false),
                canonicalHealthQuestion(health.convulsions(), "convulsions", false),
                canonicalHealthQuestion(health.frequentFainting(), "frequentFainting", false),
                canonicalHealthQuestion(health.heartCondition(), "heartCondition", false),
                canonicalHealthQuestion(health.otherHealthCondition(), "otherHealthCondition", false),
                optionalText(health.otherCare(), "health.otherCare", 5_000)
        );
    }

    private HealthQuestionDTO canonicalHealthQuestion(
            HealthQuestionDTO question,
            String field,
            boolean importantInstructionsAllowed
    ) {
        if (question == null || question.answer() == null) {
            throw new IllegalArgumentException(field + " answer is required");
        }
        String explanation = optionalText(question.explanation(), field + ".explanation", 2_000);
        if (question.answer() == HealthAnswer.YES && explanation == null) {
            throw new IllegalArgumentException(field + " explanation is required");
        }
        if (question.answer() != HealthAnswer.YES && explanation != null) {
            throw new IllegalArgumentException(field + " explanation contradicts the answer");
        }
        String importantInstructions = optionalText(
                question.importantInstructions(),
                field + ".importantInstructions",
                2_000
        );
        if (!importantInstructionsAllowed && importantInstructions != null) {
            throw new IllegalArgumentException(
                    field + " does not accept important instructions"
            );
        }
        return new HealthQuestionDTO(
                question.answer(),
                explanation,
                importantInstructions
        );
    }

    private void assertProfileReplacementAllowed(
            OratorianoEntity oratoriano,
            FormRow form,
            CompletionData completion,
            boolean overwriteNewerProfileValues
    ) {
        assertFieldReplacementAllowed(
                "name",
                !Objects.equals(oratoriano.getName(), completion.name()),
                oratoriano.getNameSourceSignedOn(),
                oratoriano.getNameManualUpdatedAt(),
                form.signedOn(),
                form.id(),
                overwriteNewerProfileValues,
                oratoriano.getCreatedAt()
        );
        assertFieldReplacementAllowed(
                "birthDate",
                !Objects.equals(oratoriano.getBirthDate(), completion.birthDate()),
                oratoriano.getBirthDateSourceSignedOn(),
                oratoriano.getBirthDateManualUpdatedAt(),
                form.signedOn(),
                form.id(),
                overwriteNewerProfileValues,
                null
        );
        assertFieldReplacementAllowed(
                "phoneNumber",
                completion.phoneNumber() != null
                        && !Objects.equals(oratoriano.getPhoneNumber(), completion.phoneNumber()),
                oratoriano.getPhoneSourceSignedOn(),
                oratoriano.getPhoneManualUpdatedAt(),
                form.signedOn(),
                form.id(),
                overwriteNewerProfileValues,
                null
        );
    }

    private void assertFieldReplacementAllowed(
            String field,
            boolean differs,
            LocalDate currentSourceSignedOn,
            Instant manualUpdatedAt,
            LocalDate incomingSignedOn,
            UUID formId,
            boolean overwriteNewerProfileValues,
            Instant initiallyRecordedAt
    ) {
        if (!differs) {
            return;
        }
        boolean olderThanFormSource = currentSourceSignedOn != null
                && !incomingSignedOn.isAfter(currentSourceSignedOn);
        if (olderThanFormSource) {
            throw ConflictException.resource(
                    "ORATORIANO_FORM_PROFILE_SOURCE_IS_NEWER",
                    "OratorianoForm",
                    formId,
                    "Completing this form would replace a value sourced from an equally recent or later form."
            );
        }
        Instant recordedAt = manualUpdatedAt == null ? initiallyRecordedAt : manualUpdatedAt;
        boolean recordedAfterSigning = recordedAt != null
                && !recordedAt.atZone(SAO_PAULO).toLocalDate().isBefore(incomingSignedOn);
        if (recordedAfterSigning && !overwriteNewerProfileValues) {
            throw ConflictException.resource(
                    "ORATORIANO_FORM_PROFILE_OVERWRITE_CHOICE_REQUIRED",
                    "OratorianoForm",
                    formId,
                    "Completing this form would replace a newer " + field
                            + " value and requires an explicit overwrite choice."
            );
        }
    }

    private boolean attachmentCorrespondsToSelectedSnapshot(
            List<AttachmentRow> attachments,
            UUID snapshotId
    ) {
        if (attachments.size() != 1
                || !"application/pdf".equals(attachments.getFirst().verifiedMimeType())) {
            return true;
        }
        try (PDDocument document = Loader.loadPDF(
                trimTrailingZeroPadding(attachments.getFirst().bytes())
        )) {
            String text = new PDFTextStripper().getText(document);
            return text.isBlank() || text.contains(snapshotId.toString());
        } catch (IOException exception) {
            return false;
        }
    }

    private void validateDeclarations(DeclarationsDTO declarations) {
        if (declarations == null
                || !Boolean.TRUE.equals(declarations.signerRelationshipConfirmed())
                || !Boolean.TRUE.equals(declarations.informationTruthConfirmed())
                || !Boolean.TRUE.equals(declarations.healthInformationCurrentConfirmed())
                || !Boolean.TRUE.equals(declarations.informationUseUnderstood())
                || !Boolean.TRUE.equals(declarations.formReviewed())
                || !Boolean.TRUE.equals(declarations.imageAndVoiceAuthorizationAccepted())) {
            throw new IllegalArgumentException("all declarations are required");
        }
    }

    private String requiredText(String value, String field, int maximumLength) {
        String normalized = optionalText(value, field, maximumLength);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String optionalText(String value, String field, int maximumLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    private List<AttachmentRow> activeAttachments(UUID formId) {
        return jdbcTemplate.query(
                "SELECT id, original_filename, verified_mime_type, byte_length, page_order, page_count, bytes "
                        + "FROM oratoriano_form_attachments "
                        + "WHERE form_id = ? AND deleted_at IS NULL ORDER BY page_order",
                (rs, rowNum) -> new AttachmentRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("original_filename"),
                        rs.getString("verified_mime_type"),
                        rs.getLong("byte_length"),
                        rs.getInt("page_order"),
                        rs.getInt("page_count"),
                        rs.getBytes("bytes")
                ),
                formId
        );
    }

    private void validateAttachmentCollection(List<AttachmentRow> attachments) {
        if (attachments.isEmpty()) {
            throw incomplete();
        }
        boolean pdf = attachments.size() == 1
                && "application/pdf".equals(attachments.getFirst().verifiedMimeType());
        if (pdf) {
            AttachmentRow attachment = attachments.getFirst();
            if (attachment.byteLength() > 20 * MEBIBYTE
                    || attachment.byteLength() != attachment.bytes().length
                    || !"application/pdf".equals(detectMimeType(attachment.bytes()))
                    || attachment.pageCount() != parsedPageCount(
                            attachment.bytes(),
                            attachment.verifiedMimeType()
                    )) {
                throw incomplete();
            }
            return;
        }
        if (attachments.size() > 10) {
            throw incomplete();
        }
        long total = 0;
        for (int index = 0; index < attachments.size(); index++) {
            AttachmentRow attachment = attachments.get(index);
            String detected = detectMimeType(attachment.bytes());
            if (attachment.pageOrder() != index + 1
                    || !detected.equals(attachment.verifiedMimeType())
                    || !("image/jpeg".equals(detected) || "image/png".equals(detected))
                    || attachment.byteLength() != attachment.bytes().length
                    || attachment.byteLength() > 8 * MEBIBYTE
                    || attachment.pageCount() != parsedPageCount(
                            attachment.bytes(),
                            attachment.verifiedMimeType()
                    )) {
                throw incomplete();
            }
            total += attachment.byteLength();
        }
        if (total > 40 * MEBIBYTE) {
            throw incomplete();
        }
    }

    private List<NewAttachment> attachmentUploads(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw InvalidCommandException.reason("A complete signed attachment is required.");
        }
        List<NewAttachment> uploads = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                byte[] bytes = file.getBytes();
                String verifiedMime = detectMimeType(bytes);
                String declaredMime = file.getContentType();
                if (declaredMime == null
                        || !normalizedDeclaredMime(declaredMime).equals(verifiedMime)) {
                    throw InvalidCommandException.reason(
                            "The declared signed-attachment MIME type does not match its content."
                    );
                }
                String filename = requiredText(file.getOriginalFilename(), "filename", 255);
                uploads.add(new NewAttachment(
                        filename,
                        verifiedMime,
                        parsedPageCount(bytes, verifiedMime),
                        bytes
                ));
            } catch (IOException exception) {
                throw InvalidCommandException.reason("The signed attachment could not be read.");
            }
        }
        List<AttachmentRow> validationRows = new ArrayList<>();
        for (int index = 0; index < uploads.size(); index++) {
            NewAttachment upload = uploads.get(index);
            validationRows.add(new AttachmentRow(
                    null,
                    upload.originalFilename(),
                    upload.verifiedMimeType(),
                    upload.bytes().length,
                    index + 1,
                    upload.pageCount(),
                    upload.bytes()
            ));
        }
        validateAttachmentCollection(validationRows);
        return List.copyOf(uploads);
    }

    private int attachmentPageCount(List<AttachmentRow> attachments) {
        return attachments.stream().mapToInt(AttachmentRow::pageCount).sum();
    }

    private int parsedPageCount(byte[] bytes, String mimeType) {
        try {
            if ("application/pdf".equals(mimeType)) {
                byte[] parseableBytes = trimTrailingZeroPadding(bytes);
                try (PDDocument document = Loader.loadPDF(parseableBytes)) {
                    int pages = document.getNumberOfPages();
                    if (pages < 1) {
                        throw InvalidCommandException.reason(
                                "The signed PDF must contain at least one page."
                        );
                    }
                    return pages;
                }
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
                throw InvalidCommandException.reason(
                        "The signed image content could not be decoded."
                );
            }
            return 1;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof InvalidCommandException invalid) {
                throw invalid;
            }
            throw InvalidCommandException.reason(
                    "The signed attachment content could not be parsed."
            );
        }
    }

    private byte[] trimTrailingZeroPadding(byte[] bytes) {
        int length = bytes.length;
        while (length > 0 && bytes[length - 1] == 0) {
            length--;
        }
        if (length == bytes.length) {
            return bytes;
        }
        return java.util.Arrays.copyOf(bytes, length);
    }

    private String normalizedDeclaredMime(String declaredMime) {
        if ("image/jpg".equalsIgnoreCase(declaredMime)) {
            return "image/jpeg";
        }
        return declaredMime.toLowerCase();
    }

    private String detectMimeType(byte[] bytes) {
        if (bytes.length >= 5
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F'
                && bytes[4] == '-') {
            return "application/pdf";
        }
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G'
                && bytes[4] == '\r'
                && bytes[5] == '\n'
                && (bytes[6] & 0xff) == 0x1a
                && bytes[7] == '\n') {
            return "image/png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        throw InvalidCommandException.reason("Unsupported signed-attachment content.");
    }

    private InvalidCommandException incomplete() {
        return InvalidCommandException.reason(
                "The additional-form draft is incomplete and cannot be completed."
        );
    }

    private String json(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Oratoriano form data.", exception);
        }
    }

    private Map<String, Object> map(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read Oratoriano form data.", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private byte[] printablePdf(UUID oratorianoId, UUID formId, PrintPdfData snapshot) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont font = unicodePdfFont(document);
            List<List<String>> pages = printablePages(oratorianoId, formId, snapshot, font);
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, PDF_FONT_SIZE);
                    content.setLeading(PDF_LEADING);
                    content.newLineAtOffset(PDF_MARGIN, page.getMediaBox().getHeight() - PDF_MARGIN);
                    for (String line : pageHeader(oratorianoId, formId, snapshot, pageIndex + 1, pages.size())) {
                        content.showText(line);
                        content.newLine();
                    }
                    for (String line : pages.get(pageIndex)) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render Oratoriano form PDF.", exception);
        }
    }

    private int printablePageCount(UUID oratorianoId, UUID formId, PrintPdfData snapshot) {
        try (PDDocument document = new PDDocument()) {
            PDFont font = unicodePdfFont(document);
            return printablePages(oratorianoId, formId, snapshot, font).size();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to measure Oratoriano form PDF.", exception);
        }
    }

    private PDFont unicodePdfFont(PDDocument document) throws IOException {
        var mapping = FontMappers.instance().getTrueTypeFont("Arial", null);
        if (mapping == null || mapping.getFont() == null) {
            throw new IOException("No Unicode TrueType font is available for form rendering.");
        }
        return PDType0Font.load(document, mapping.getFont(), true);
    }

    private List<List<String>> printablePages(
            UUID oratorianoId,
            UUID formId,
            PrintPdfData snapshot,
            PDFont font
    ) throws IOException {
        float writableWidth = PDRectangle.A4.getWidth() - (2 * PDF_MARGIN);
        int bodyLinesPerPage = Math.max(
                1,
                (int) ((PDRectangle.A4.getHeight() - (2 * PDF_MARGIN)) / PDF_LEADING) - PDF_HEADER_LINES
        );
        List<String> wrappedLines = new ArrayList<>();
        for (String line : printableBodyLines(snapshot)) {
            wrappedLines.addAll(wrapPdfLine(line, font, writableWidth));
        }
        if (wrappedLines.isEmpty()) {
            wrappedLines.add("");
        }

        List<List<String>> pages = new ArrayList<>();
        for (int offset = 0; offset < wrappedLines.size(); offset += bodyLinesPerPage) {
            pages.add(new ArrayList<>(
                    wrappedLines.subList(offset, Math.min(offset + bodyLinesPerPage, wrappedLines.size()))
            ));
        }
        return pages;
    }

    private List<String> pageHeader(
            UUID oratorianoId,
            UUID formId,
            PrintPdfData snapshot,
            int pageNumber,
            int pageCount
    ) {
        return List.of(
                "Oratoriano: " + oratorianoId + " - Form: " + formId,
                "Print snapshot: " + snapshot.id() + " - Template version: " + TEMPLATE_VERSION,
                "Generated at: " + snapshot.generatedAt() + " - Mode: " + snapshot.mode(),
                "Page " + pageNumber + " of " + pageCount + " - Snapshot " + snapshot.id()
        );
    }

    private List<String> printableBodyLines(PrintPdfData snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add("Signing location / Local de assinatura: Piracicaba");

        if (snapshot.mode() == PrintMode.IDENTIFIED_BLANK) {
            lines.addAll(blankFormLines());
        } else {
            flattenPrintableData(lines, "", snapshot.capturedData());
        }
        lines.add("Signer relationship confirmed / Relação do signatário - Initial / Rubrica: ______");
        lines.add("Information is true / Informações são verdadeiras - Initial / Rubrica: ______");
        lines.add("Health information is current / Informações de saúde atuais - Initial / Rubrica: ______");
        lines.add("Information use understood / Uso das informações compreendido - Initial / Rubrica: ______");
        lines.add("Form reviewed / Formulário revisado - Initial / Rubrica: ______");
        lines.add("Image and voice authorization / Autorização de imagem e voz - Initial / Rubrica: ______");
        lines.add("Signature date / Data da assinatura: ____________________");
        lines.add("Full signature / Assinatura completa: ____________________________________");
        return lines;
    }

    private List<String> wrapPdfLine(String rawLine, PDFont font, float writableWidth) throws IOException {
        String line = rawLine == null ? "" : rawLine.replaceAll("\\s+", " ").trim();
        if (line.isEmpty()) {
            return List.of("");
        }

        List<String> wrapped = new ArrayList<>();
        String remaining = line;
        while (!remaining.isEmpty()) {
            if (pdfTextWidth(font, remaining) <= writableWidth) {
                wrapped.add(remaining);
                break;
            }

            int split = largestFittingPrefix(remaining, font, writableWidth);
            int whitespace = remaining.lastIndexOf(' ', split);
            if (whitespace > 0) {
                split = whitespace;
            }
            wrapped.add(remaining.substring(0, split).trim());
            remaining = remaining.substring(split).trim();
        }
        return wrapped;
    }

    private int largestFittingPrefix(String value, PDFont font, float writableWidth) throws IOException {
        int low = 1;
        int high = value.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (pdfTextWidth(font, value.substring(0, middle)) <= writableWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private float pdfTextWidth(PDFont font, String value) throws IOException {
        return font.getStringWidth(value) / 1000 * PDF_FONT_SIZE;
    }

    private List<String> blankFormLines() {
        return List.of(
                "First name / Nome: ____________________________________",
                "Surname / Sobrenome: __________________________________",
                "Birth date / Data de nascimento: ______________________",
                "CPF: __________________  RG: __________________",
                "Address / Endereço: ___________________________________",
                "Address number: __________  Neighborhood: ______________",
                "CEP: __________  City: _________________________________",
                "Phone number: _________________________________",
                "School / Escola: __________________  School grade: _______",
                "Responsible / Responsável: _____________________________",
                "Responsible relationship and complement: _______________",
                "Responsible at least 18 years old: YES [ ]",
                "Responsible CPF, phone, and email: ______________________",
                "Father / Pai: __________________________________________",
                "Mother / Mãe: __________________________________________",
                "Health / Saúde",
                "Medical follow-up: YES [ ] NO [ ] NOT INFORMED [ ] Explanation: __________________",
                "Physical activity restriction: YES [ ] NO [ ] NOT INFORMED [ ] Explanation: ______",
                "Medicine use: YES [ ] NO [ ] NOT INFORMED [ ] Explanation: ______________________",
                "Important instructions: ________________________________________________________",
                "Allergies: YES [ ] NO [ ] NOT INFORMED [ ] Explanation: _________________________",
                "Convulsions: YES [ ] NO [ ] NOT INFORMED [ ] Explanation: _______________________",
                "Frequent fainting: YES [ ] NO [ ] NOT INFORMED [ ] Explanation: _________________",
                "Heart condition: YES [ ] NO [ ] NOT INFORMED [ ] Explanation: ___________________",
                "Other health condition: YES [ ] NO [ ] NOT INFORMED [ ] Explanation: ____________",
                "Other care: ____________________________________________________________________"
        );
    }

    private void flattenPrintableData(
            List<String> lines,
            String prefix,
            Map<?, ?> values
    ) {
        values.forEach((key, value) -> {
            String label = prefix.isEmpty() ? humanizeField(key.toString()) : prefix + " " + humanizeField(key.toString());
            if (value instanceof Map<?, ?> nested) {
                flattenPrintableData(lines, label, nested);
            } else if (value != null) {
                lines.add(label + ": " + value);
            }
        });
    }

    private String humanizeField(String field) {
        String spaced = field.replaceAll("([a-z])([A-Z])", "$1 $2");
        if (spaced.isEmpty()) {
            return spaced;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private AccountReferenceRDTO accountReference(UUID accountId) {
        return accountId == null ? null : new AccountReferenceRDTO(accountId);
    }

    private void activity(
            ActivityAction action,
            UUID formId,
            String reason,
            String summary,
            Map<String, Object> metadata
    ) {
        activityEvents.moduleActivity(
                action,
                ActivityTargetType.ORATORIANO_FORM,
                formId,
                reason,
                summary,
                metadata
        );
    }

    private record FormRow(
            UUID id,
            UUID oratorianoId,
            int version,
            FormStatus status,
            FormOrigin origin,
            long draftRevision,
            Map<String, Object> data,
            LocalDate signedOn,
            UUID createdBy,
            Instant createdAt
    ) {
        FormRDTO toRDTO() {
            return new FormRDTO(
                    id,
                    oratorianoId,
                    version,
                    status,
                    origin,
                    draftRevision,
                    data,
                    signedOn,
                    new AccountReferenceRDTO(createdBy),
                    createdAt
            );
        }
    }

    public record AttachmentDownload(
            String originalFilename,
            String verifiedMimeType,
            byte[] bytes
    ) {
    }

    private record CompletionData(
            GamName name,
            LocalDate birthDate,
            GamPhoneNumber phoneNumber,
            Map<String, Object> canonicalData
    ) {
    }

    private record LatestSnapshot(
            UUID id,
            long draftRevision,
            int pageCount
    ) {
    }

    private record PrintPdfData(
            UUID id,
            PrintMode mode,
            Instant generatedAt,
            int pageCount,
            Map<String, Object> capturedData
    ) {
    }

    private record AttachmentRow(
            UUID id,
            String originalFilename,
            String verifiedMimeType,
            long byteLength,
            int pageOrder,
            int pageCount,
            byte[] bytes
    ) {
    }

    private record NewAttachment(
            String originalFilename,
            String verifiedMimeType,
            int pageCount,
            byte[] bytes
    ) {
    }
}
