package br.org.gam.api.event.oratorio.web;

import br.org.gam.api.event.oratorio.application.OratorioApiModels.CreateOratorioDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.OratorioRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.PlanningDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.PresentSummaryRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.QuickRegistrationRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.ReasonDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.ReopenDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.RosterEntryRDTO;
import br.org.gam.api.event.oratorio.application.OratorioApiModels.TeamType;
import br.org.gam.api.event.oratorio.application.useCases.OratorioOperations;
import br.org.gam.api.event.oratorio.application.useCases.OratorioOperations.AttendanceMutation;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.RegisterOratorianoDTO;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.shared.web.PagedResponse;
import br.org.gam.api.shared.web.PublicApiUri;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oratorios")
@Tag(name = "Oratorios")
public class OratorioController {
    private final OratorioOperations operations;

    OratorioController(OratorioOperations operations) {
        this.operations = operations;
    }

    @Operation(operationId = "createOratorio", summary = "Create an Oratorio occurrence from a local date")
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_CREATE + "')")
    public ResponseEntity<OratorioRDTO> create(@RequestBody @Valid CreateOratorioDTO dto) {
        OratorioRDTO result = operations.create(dto);
        return ResponseEntity.created(PublicApiUri.forResource("/oratorios/" + result.id())).body(result);
    }

    @Operation(operationId = "getOratorio", summary = "Read specialized Oratorio occurrence detail")
    @GetMapping("/{oratorioId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_GET + "')")
    public ResponseEntity<OratorioRDTO> get(@PathVariable UUID oratorioId) {
        return ResponseEntity.ok(operations.get(oratorioId));
    }

    @Operation(operationId = "replaceOratorioPlanning", summary = "Replace Oratorio planning text")
    @PutMapping("/{oratorioId}/planning")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_MANAGE + "')")
    public ResponseEntity<OratorioRDTO> planning(
            @PathVariable UUID oratorioId,
            @RequestBody @Valid PlanningDTO dto
    ) {
        return ResponseEntity.ok(operations.replacePlanning(oratorioId, dto));
    }

    @Operation(operationId = "assignOratorioTeamMember", summary = "Assign a Member to an Oratorio team")
    @PutMapping("/{oratorioId}/teams/{teamType}/members/{memberId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_MANAGE + "')")
    public ResponseEntity<Void> assignTeam(
            @PathVariable UUID oratorioId,
            @PathVariable TeamType teamType,
            @PathVariable UUID memberId
    ) {
        operations.assignTeamMember(oratorioId, teamType, memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "removeOratorioTeamMember", summary = "Remove a Member from an Oratorio team")
    @DeleteMapping("/{oratorioId}/teams/{teamType}/members/{memberId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_MANAGE + "')")
    public ResponseEntity<Void> removeTeam(
            @PathVariable UUID oratorioId,
            @PathVariable TeamType teamType,
            @PathVariable UUID memberId
    ) {
        operations.removeTeamMember(oratorioId, teamType, memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "lockOratorio", summary = "Lock Oratorio attendance")
    @PatchMapping("/{oratorioId}/lock")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_MANAGE + "')")
    public ResponseEntity<Void> lock(@PathVariable UUID oratorioId) {
        operations.lock(oratorioId);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "finalizeOratorio", summary = "Finalize an Oratorio occurrence")
    @PatchMapping("/{oratorioId}/finalize")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_MANAGE + "')")
    public ResponseEntity<Void> finalizeOccurrence(@PathVariable UUID oratorioId) {
        operations.finalizeOccurrence(oratorioId);
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "cancelOratorio", summary = "Cancel an Oratorio occurrence")
    @PatchMapping("/{oratorioId}/cancel")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_MANAGE + "')")
    public ResponseEntity<Void> cancel(@PathVariable UUID oratorioId, @RequestBody ReasonDTO dto) {
        operations.cancel(oratorioId, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "reopenOratorio", summary = "Reopen Oratorio planning or attendance")
    @PatchMapping("/{oratorioId}/reopen")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_MANAGE + "')")
    public ResponseEntity<Void> reopen(@PathVariable UUID oratorioId, @RequestBody @Valid ReopenDTO dto) {
        operations.reopen(oratorioId, dto.targetStatus(), dto.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "deleteOratorio", summary = "Delete an erroneous Oratorio occurrence")
    @DeleteMapping("/{oratorioId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_MANAGE + "')")
    public ResponseEntity<Void> delete(@PathVariable UUID oratorioId, @RequestBody ReasonDTO dto) {
        operations.delete(oratorioId, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "getOratorioMemberAttendanceRoster", summary = "Read a Member tracker roster page")
    @GetMapping("/{oratorioId}/attendance/members")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_ATTENDANCE_GET + "')")
    public ResponseEntity<PagedResponse<RosterEntryRDTO>> memberRoster(
            @PathVariable UUID oratorioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String name
    ) {
        return ResponseEntity.ok(operations.memberRoster(oratorioId, page, name));
    }

    @Operation(operationId = "getOratorioOratorianoAttendanceRoster", summary = "Read an Oratoriano tracker roster page")
    @GetMapping("/{oratorioId}/attendance/oratorianos")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_ATTENDANCE_GET + "')")
    public ResponseEntity<PagedResponse<RosterEntryRDTO>> oratorianoRoster(
            @PathVariable UUID oratorioId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String name
    ) {
        return ResponseEntity.ok(operations.oratorianoRoster(oratorioId, page, name));
    }

    @Operation(operationId = "getOratorioPresentSummary", summary = "Read the persistent present summary")
    @GetMapping("/{oratorioId}/attendance/present")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_ATTENDANCE_GET + "')")
    public ResponseEntity<PresentSummaryRDTO> present(@PathVariable UUID oratorioId) {
        return ResponseEntity.ok(operations.present(oratorioId));
    }

    @Operation(operationId = "markOratorioMemberPresent", summary = "Idempotently mark a Member present")
    @PutMapping("/{oratorioId}/attendance/members/{memberId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_ATTENDANCE_MANAGE + "')")
    public ResponseEntity<br.org.gam.api.event.oratorio.application.OratorioApiModels.AttendanceRDTO> markMember(
            @PathVariable UUID oratorioId,
            @PathVariable UUID memberId
    ) {
        return attendanceResponse(operations.markMember(oratorioId, memberId));
    }

    @Operation(operationId = "uncheckOratorioMember", summary = "Idempotently remove Member attendance")
    @DeleteMapping("/{oratorioId}/attendance/members/{memberId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_ATTENDANCE_MANAGE + "')")
    public ResponseEntity<Void> uncheckMember(
            @PathVariable UUID oratorioId,
            @PathVariable UUID memberId,
            @RequestBody(required = false) ReasonDTO dto
    ) {
        operations.uncheckMember(oratorioId, memberId, dto == null ? null : dto.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "markOratorianoPresent", summary = "Idempotently mark an Oratoriano present")
    @PutMapping("/{oratorioId}/attendance/oratorianos/{oratorianoId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_ATTENDANCE_MANAGE + "')")
    public ResponseEntity<br.org.gam.api.event.oratorio.application.OratorioApiModels.AttendanceRDTO> markOratoriano(
            @PathVariable UUID oratorioId,
            @PathVariable UUID oratorianoId
    ) {
        return attendanceResponse(operations.markOratoriano(oratorioId, oratorianoId));
    }

    @Operation(operationId = "uncheckOratoriano", summary = "Idempotently remove Oratoriano attendance")
    @DeleteMapping("/{oratorioId}/attendance/oratorianos/{oratorianoId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_ATTENDANCE_MANAGE + "')")
    public ResponseEntity<Void> uncheckOratoriano(
            @PathVariable UUID oratorioId,
            @PathVariable UUID oratorianoId,
            @RequestBody(required = false) ReasonDTO dto
    ) {
        operations.uncheckOratoriano(oratorioId, oratorianoId, dto == null ? null : dto.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "registerAndMarkOratorianoPresent", summary = "Atomically register and mark present")
    @PostMapping("/{oratorioId}/attendance/oratorianos/register-and-mark")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIO_ATTENDANCE_MANAGE + "') "
            + "and hasAuthority('" + PermissionEnum.Code.ORATORIANO_REGISTER + "')")
    public ResponseEntity<QuickRegistrationRDTO> registerAndMark(
            @PathVariable UUID oratorioId,
            @RequestBody @Valid RegisterOratorianoDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(operations.registerAndMark(oratorioId, dto));
    }

    private ResponseEntity<br.org.gam.api.event.oratorio.application.OratorioApiModels.AttendanceRDTO>
            attendanceResponse(AttendanceMutation mutation) {
        return ResponseEntity.status(mutation.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(mutation.attendance());
    }
}
