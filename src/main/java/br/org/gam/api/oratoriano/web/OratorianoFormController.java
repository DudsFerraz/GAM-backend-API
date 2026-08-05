package br.org.gam.api.oratoriano.web;

import br.org.gam.api.oratoriano.application.OratorianoApiModels.ReasonDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.AttachmentRDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.CreateFormDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.CompleteFormDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormDraftDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormHistoryRDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.FormRDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.PrintSnapshotRDTO;
import br.org.gam.api.oratoriano.application.OratorianoFormApiModels.PrintSnapshotMetadataRDTO;
import br.org.gam.api.oratoriano.application.useCases.OratorianoForms;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.shared.web.PagedResponse;
import br.org.gam.api.shared.web.PublicApiUri;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/oratorianos/{oratorianoId}/forms")
@Tag(name = "Oratoriano Forms")
public class OratorianoFormController {
    private final OratorianoForms forms;

    OratorianoFormController(OratorianoForms forms) {
        this.forms = forms;
    }

    @Operation(operationId = "createOratorianoFormDraft", summary = "Create an Oratoriano additional-form draft")
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_MANAGE + "')")
    public ResponseEntity<FormRDTO> create(
            @PathVariable UUID oratorianoId,
            @RequestBody @Valid CreateFormDTO dto
    ) {
        FormRDTO result = forms.create(oratorianoId, dto);
        return ResponseEntity.created(PublicApiUri.forResource(
                "/oratorianos/" + oratorianoId + "/forms/" + result.id()
        )).body(result);
    }

    @Operation(operationId = "getOratorianoFormHistory", summary = "Read metadata-only form history")
    @GetMapping
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_GET + "')")
    public ResponseEntity<PagedResponse<FormHistoryRDTO>> history(
            @PathVariable UUID oratorianoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(forms.history(oratorianoId, page, size));
    }

    @Operation(operationId = "getOratorianoFormDetail", summary = "Read audited sensitive form detail")
    @GetMapping("/{formId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_GET + "')")
    public ResponseEntity<FormRDTO> detail(
            @PathVariable UUID oratorianoId,
            @PathVariable UUID formId
    ) {
        return ResponseEntity.ok(forms.detail(oratorianoId, formId));
    }

    @Operation(operationId = "replaceOratorianoFormDraft", summary = "Replace editable draft transcription")
    @PutMapping("/{formId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_MANAGE + "')")
    public ResponseEntity<FormRDTO> replace(
            @PathVariable UUID oratorianoId,
            @PathVariable UUID formId,
            @RequestBody @Valid FormDraftDTO dto
    ) {
        return ResponseEntity.ok(forms.replaceDraft(oratorianoId, formId, dto));
    }

    @Operation(operationId = "deleteOratorianoFormDraft", summary = "Delete an additional-form draft")
    @DeleteMapping("/{formId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_MANAGE + "')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID oratorianoId,
            @PathVariable UUID formId,
            @RequestBody ReasonDTO dto
    ) {
        forms.deleteDraft(oratorianoId, formId, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "completeOratorianoForm", summary = "Complete an additional-form draft")
    @PatchMapping("/{formId}/complete")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_MANAGE + "')")
    public ResponseEntity<Void> complete(
            @PathVariable UUID oratorianoId,
            @PathVariable UUID formId,
            @RequestBody @Valid CompleteFormDTO dto
    ) {
        forms.complete(
                oratorianoId,
                formId,
                dto.printSnapshotId(),
                dto != null && Boolean.TRUE.equals(dto.overwriteNewerProfileValues())
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "revokeOratorianoForm", summary = "Revoke the current completed form")
    @PatchMapping("/{formId}/revoke")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_MANAGE + "')")
    public ResponseEntity<Void> revoke(
            @PathVariable UUID oratorianoId,
            @PathVariable UUID formId,
            @RequestBody ReasonDTO dto
    ) {
        forms.revoke(oratorianoId, formId, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "createOratorianoFormPrintSnapshot", summary = "Create an immutable print snapshot")
    @PostMapping("/{formId}/print-snapshots")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_PDF_GENERATE + "')")
    public ResponseEntity<PrintSnapshotRDTO> createPrintSnapshot(
            @PathVariable UUID oratorianoId,
            @PathVariable UUID formId
    ) {
        return ResponseEntity.status(201).body(forms.createPrintSnapshot(oratorianoId, formId));
    }

    @Operation(
            operationId = "getOratorianoFormPrintSnapshots",
            summary = "Recover print-snapshot metadata",
            description = "Lists every active immutable print snapshot for the form, including older draft revisions."
    )
    @GetMapping("/{formId}/print-snapshots")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_PDF_GENERATE + "')")
    public ResponseEntity<PagedResponse<PrintSnapshotMetadataRDTO>> printSnapshots(
            @PathVariable UUID oratorianoId,
            @PathVariable UUID formId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) List<String> sort
    ) {
        return ResponseEntity.ok(forms.printSnapshots(oratorianoId, formId, page, size, sort));
    }

    @Operation(operationId = "renderOratorianoFormPdf", summary = "Render a disposable identified PDF")
    @GetMapping("/{formId}/print-snapshots/{printSnapshotId}/pdf")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_PDF_GENERATE + "')")
    public ResponseEntity<byte[]> renderPdf(
            @PathVariable UUID oratorianoId,
            @PathVariable UUID formId,
            @PathVariable UUID printSnapshotId
    ) {
        byte[] bytes = forms.renderPdf(oratorianoId, formId, printSnapshotId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("oratoriano-form-" + formId + ".pdf")
                                .build()
                                .toString()
                )
                .body(bytes);
    }

    @Operation(
            operationId = "replaceOratorianoFormSignedAttachments",
            summary = "Replace a draft's complete signed attachment collection"
    )
    @PutMapping(value = "/{formId}/signed-attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_MANAGE + "')")
    public ResponseEntity<List<AttachmentRDTO>> replaceSignedAttachments(
            @PathVariable UUID oratorianoId,
            @PathVariable UUID formId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return ResponseEntity.ok(forms.replaceAttachments(oratorianoId, formId, files));
    }

    @Operation(
            operationId = "getOratorianoFormSignedAttachments",
            summary = "Recover active signed-attachment metadata",
            description = "Lists the form's active signed-attachment collection in page order without reading file bytes."
    )
    @GetMapping("/{formId}/signed-attachments")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_ATTACHMENT_GET + "')")
    public ResponseEntity<List<AttachmentRDTO>> signedAttachments(
            @PathVariable UUID oratorianoId,
            @PathVariable UUID formId
    ) {
        return ResponseEntity.ok(forms.attachments(oratorianoId, formId));
    }

    @Operation(
            operationId = "downloadOratorianoFormSignedAttachment",
            summary = "Download one private signed-form attachment"
    )
    @GetMapping("/{formId}/signed-attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_FORM_ATTACHMENT_GET + "')")
    public ResponseEntity<byte[]> downloadSignedAttachment(
            @PathVariable UUID oratorianoId,
            @PathVariable UUID formId,
            @PathVariable UUID attachmentId
    ) {
        OratorianoForms.AttachmentDownload attachment =
                forms.downloadAttachment(oratorianoId, formId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.verifiedMimeType()))
                .contentLength(attachment.bytes().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(attachment.originalFilename())
                                .build()
                                .toString()
                )
                .body(attachment.bytes());
    }
}
