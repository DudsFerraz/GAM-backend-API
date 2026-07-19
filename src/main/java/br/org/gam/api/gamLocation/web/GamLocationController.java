package br.org.gam.api.gamLocation.web;

import br.org.gam.api.gamLocation.application.GamLocationRDTO;
import br.org.gam.api.gamLocation.application.useCases.CreateGamLocation;
import br.org.gam.api.gamLocation.application.useCases.GetGamLocations;
import br.org.gam.api.gamLocation.application.useCases.GamLocationMutationDTO;
import br.org.gam.api.gamLocation.application.useCases.RemoveGamLocation;
import br.org.gam.api.gamLocation.application.useCases.RemoveGamLocationDTO;
import br.org.gam.api.gamLocation.application.useCases.UpdateGamLocation;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.shared.web.PagedResponse;
import br.org.gam.api.shared.web.PublicApiUri;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gam-locations")
public class GamLocationController {
    private final CreateGamLocation createGamLocation;
    private final GetGamLocations getGamLocations;
    private final UpdateGamLocation updateGamLocation;
    private final RemoveGamLocation removeGamLocation;

    public GamLocationController(CreateGamLocation createGamLocation, GetGamLocations getGamLocations,
                                 UpdateGamLocation updateGamLocation, RemoveGamLocation removeGamLocation) {
        this.createGamLocation = createGamLocation;
        this.getGamLocations = getGamLocations;
        this.updateGamLocation = updateGamLocation;
        this.removeGamLocation = removeGamLocation;
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.GAM_LOCATION_CREATE + "')")
    @Operation(operationId = "createGamLocation", summary = "Create a GamLocation")
    @PostMapping
    public ResponseEntity<GamLocationRDTO> create(@RequestBody @Valid GamLocationMutationDTO dto) {
        GamLocationRDTO response = createGamLocation.create(dto);
        return ResponseEntity.created(PublicApiUri.forResource("/gam-locations/" + response.id()))
                .body(response);
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.GAM_LOCATION_GET + "')")
    @Operation(operationId = "getGamLocation", summary = "Get an active GamLocation")
    @GetMapping("/{id}")
    public ResponseEntity<GamLocationRDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(getGamLocations.byId(id));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.GAM_LOCATION_GET + "')")
    @Operation(operationId = "listGamLocations", summary = "List active GamLocations")
    @GetMapping
    public ResponseEntity<PagedResponse<GamLocationRDTO>> list(Pageable pageable) {
        Pageable effectivePageable = pageable;
        if (pageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(
                    pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))
            );
        }
        return ResponseEntity.ok(PagedResponse.from(getGamLocations.all(effectivePageable)));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.GAM_LOCATION_MANAGE + "')")
    @Operation(operationId = "updateGamLocation", summary = "Replace a GamLocation")
    @PutMapping("/{id}")
    public ResponseEntity<GamLocationRDTO> update(
            @PathVariable UUID id,
            @RequestBody @Valid GamLocationMutationDTO dto
    ) {
        return ResponseEntity.ok(updateGamLocation.update(id, dto));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.GAM_LOCATION_MANAGE + "')")
    @Operation(operationId = "removeGamLocation", summary = "Remove an unused GamLocation")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID id,
            @RequestBody @Valid RemoveGamLocationDTO dto
    ) {
        removeGamLocation.remove(id, dto);
        return ResponseEntity.noContent().build();
    }
}
