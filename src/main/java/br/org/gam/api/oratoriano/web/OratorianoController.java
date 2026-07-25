package br.org.gam.api.oratoriano.web;

import br.org.gam.api.oratoriano.application.OratorianoApiModels.AttendanceHistoryItemRDTO;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.AttendanceSummaryRDTO;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.OratorianoRDTO;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.ReasonDTO;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.RegisterOratorianoDTO;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.ReplaceOratorianoDTO;
import br.org.gam.api.oratoriano.application.useCases.OratorianoRecords;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.web.PagedResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/oratorianos")
@Tag(name = "Oratorianos")
public class OratorianoController {
    private final OratorianoRecords records;

    OratorianoController(OratorianoRecords records) {
        this.records = records;
    }

    @Operation(operationId = "registerOratoriano", summary = "Register an Oratoriano from a complete name")
    @PostMapping
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_REGISTER + "')")
    public ResponseEntity<OratorianoRDTO> register(@RequestBody @Valid RegisterOratorianoDTO dto) {
        OratorianoRDTO result = records.register(dto);
        return ResponseEntity.created(PublicApiUri.forResource("/oratorianos/" + result.id())).body(result);
    }

    @Operation(operationId = "getOratoriano", summary = "Read an ordinary Oratoriano profile")
    @GetMapping("/{oratorianoId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_GET + "')")
    public ResponseEntity<OratorianoRDTO> get(@PathVariable UUID oratorianoId) {
        return ResponseEntity.ok(records.get(oratorianoId));
    }

    @Operation(operationId = "replaceOratoriano", summary = "Correct an ordinary Oratoriano profile")
    @PutMapping("/{oratorianoId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_MANAGE + "')")
    public ResponseEntity<OratorianoRDTO> replace(
            @PathVariable UUID oratorianoId,
            @RequestBody @Valid ReplaceOratorianoDTO dto
    ) {
        return ResponseEntity.ok(records.replace(oratorianoId, dto));
    }

    @Operation(operationId = "deleteOratoriano", summary = "Delete an erroneous Oratoriano record")
    @DeleteMapping("/{oratorianoId}")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_MANAGE + "')")
    public ResponseEntity<Void> delete(@PathVariable UUID oratorianoId, @RequestBody ReasonDTO dto) {
        records.delete(oratorianoId, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "restoreOratoriano", summary = "Restore a deleted Oratoriano record")
    @PatchMapping("/{oratorianoId}/restore")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_MANAGE + "')")
    public ResponseEntity<Void> restore(@PathVariable UUID oratorianoId, @RequestBody ReasonDTO dto) {
        records.restore(oratorianoId, dto.reason());
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "getOratorianoAttendances", summary = "Read active Oratorio attendance history")
    @GetMapping("/{oratorianoId}/attendances")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_GET + "')")
    public ResponseEntity<PagedResponse<AttendanceHistoryItemRDTO>> attendances(
            @PathVariable UUID oratorianoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(records.attendanceHistory(oratorianoId, page, size));
    }

    @Operation(operationId = "getOratorianoAttendanceSummary", summary = "Read derived attendance counts")
    @GetMapping("/{oratorianoId}/attendance-summary")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_GET + "')")
    public ResponseEntity<AttendanceSummaryRDTO> attendanceSummary(
            @PathVariable UUID oratorianoId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        return ResponseEntity.ok(records.attendanceSummary(oratorianoId, year, month));
    }

    @Operation(operationId = "searchOratorianos", summary = "Search ordinary Oratoriano profiles")
    @PostMapping("/search")
    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ORATORIANO_GET + "')")
    public ResponseEntity<PagedResponse<OratorianoRDTO>> search(
            @RequestBody @Valid SearchDTO search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer attendanceYear,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(records.search(search, sort, attendanceYear, page, size));
    }
}
