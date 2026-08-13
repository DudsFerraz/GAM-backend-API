package br.org.gam.api.event.missa.web;

import br.org.gam.api.event.missa.application.MissaApiModels.AssignmentDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.CreateMissaDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.MissaRDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.ReasonDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.ReopenDTO;
import br.org.gam.api.event.missa.application.MissaApiModels.ReplaceMissaDTO;
import br.org.gam.api.event.missa.application.useCases.*;
import br.org.gam.api.event.missa.domain.MissaResponsibility;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.shared.web.PublicApiUri;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/missas")
@Tag(name = "Missas")
public class MissaController {
    private final CreateMissa createMissa;
    private final GetMissa getMissa;
    private final ReplaceMissa replaceMissa;
    private final AssignMissaMember assignMissaMember;
    private final RemoveMissaMember removeMissaMember;
    private final LockMissa lockMissa;
    private final FinalizeMissa finalizeMissa;
    private final ReopenMissa reopenMissa;
    private final CancelMissa cancelMissa;
    private final DeleteMissa deleteMissa;

    MissaController(CreateMissa createMissa, GetMissa getMissa, ReplaceMissa replaceMissa,
                    AssignMissaMember assignMissaMember, RemoveMissaMember removeMissaMember,
                    LockMissa lockMissa, FinalizeMissa finalizeMissa, ReopenMissa reopenMissa,
                    CancelMissa cancelMissa, DeleteMissa deleteMissa) {
        this.createMissa = createMissa;
        this.getMissa = getMissa;
        this.replaceMissa = replaceMissa;
        this.assignMissaMember = assignMissaMember;
        this.removeMissaMember = removeMissaMember;
        this.lockMissa = lockMissa;
        this.finalizeMissa = finalizeMissa;
        this.reopenMissa = reopenMissa;
        this.cancelMissa = cancelMissa;
        this.deleteMissa = deleteMissa;
    }

    @Operation(operationId = "createMissa", summary = "Create a Missa without assignments")
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MISSA_CREATE + "')")
    public ResponseEntity<MissaRDTO> create(@RequestBody @Valid CreateMissaDTO dto) {
        MissaRDTO result = createMissa.create(dto);
        return ResponseEntity.created(PublicApiUri.forResource("/missas/" + result.id())).body(result);
    }

    @Operation(operationId = "getMissa", summary = "Read specialized Missa detail")
    @GetMapping("/{missaId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MISSA_GET + "')")
    public ResponseEntity<MissaRDTO> get(@PathVariable UUID missaId) {
        return ResponseEntity.ok(getMissa.byId(missaId));
    }

    @Operation(operationId = "replaceMissa", summary = "Replace mutable Missa Event fields")
    @PutMapping("/{missaId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MISSA_MANAGE + "')")
    public ResponseEntity<MissaRDTO> replace(
            @PathVariable UUID missaId,
            @RequestBody @Valid ReplaceMissaDTO dto
    ) {
        return ResponseEntity.ok(replaceMissa.replace(missaId, dto));
    }

    @Operation(operationId = "assignMissaMember", summary = "Assign a Member to a liturgical responsibility")
    @PutMapping("/{missaId}/assignments/{responsibility}/members/{memberId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MISSA_MANAGE + "')")
    public ResponseEntity<MissaRDTO> assign(
            @PathVariable UUID missaId,
            @PathVariable MissaResponsibility responsibility,
            @PathVariable UUID memberId,
            @RequestBody(required = false) @Valid AssignmentDTO dto
    ) {
        return ResponseEntity.ok(assignMissaMember.assign(
                missaId, responsibility, memberId, dto == null ? null : dto.reason()
        ));
    }

    @Operation(operationId = "removeMissaMember", summary = "Remove one liturgical assignment")
    @DeleteMapping("/{missaId}/assignments/{responsibility}/members/{memberId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MISSA_MANAGE + "')")
    public ResponseEntity<Void> remove(
            @PathVariable UUID missaId,
            @PathVariable MissaResponsibility responsibility,
            @PathVariable UUID memberId,
            @RequestBody(required = false) @Valid AssignmentDTO dto
    ) {
        removeMissaMember.remove(missaId, responsibility, memberId, dto == null ? null : dto.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "lockMissa", summary = "Lock Missa attendance")
    @PatchMapping("/{missaId}/lock")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MISSA_MANAGE + "')")
    public ResponseEntity<MissaRDTO> lock(@PathVariable UUID missaId) {
        return ResponseEntity.ok(lockMissa.lock(missaId));
    }

    @Operation(operationId = "finalizeMissa", summary = "Finalize a Missa")
    @PatchMapping("/{missaId}/finalize")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MISSA_MANAGE + "')")
    public ResponseEntity<MissaRDTO> finalizeMissa(@PathVariable UUID missaId) {
        return ResponseEntity.ok(finalizeMissa.finalizeMissa(missaId));
    }

    @Operation(operationId = "reopenMissa", summary = "Reopen a Missa with a reason")
    @PatchMapping("/{missaId}/reopen")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MISSA_MANAGE + "')")
    public ResponseEntity<MissaRDTO> reopen(
            @PathVariable UUID missaId,
            @RequestBody @Valid ReopenDTO dto
    ) {
        return ResponseEntity.ok(reopenMissa.reopen(missaId, dto));
    }

    @Operation(operationId = "cancelMissa", summary = "Cancel a Missa with a reason")
    @PatchMapping("/{missaId}/cancel")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MISSA_MANAGE + "')")
    public ResponseEntity<MissaRDTO> cancel(
            @PathVariable UUID missaId,
            @RequestBody @Valid ReasonDTO dto
    ) {
        return ResponseEntity.ok(cancelMissa.cancel(missaId, dto.reason()));
    }

    @Operation(operationId = "deleteMissa", summary = "Delete an erroneous Missa")
    @DeleteMapping("/{missaId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.MISSA_MANAGE + "')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID missaId,
            @RequestBody @Valid ReasonDTO dto
    ) {
        deleteMissa.delete(missaId, dto.reason());
        return ResponseEntity.noContent().build();
    }
}
