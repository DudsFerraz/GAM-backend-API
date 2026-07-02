package br.org.gam.api.location.web;

import br.org.gam.api.location.application.LocationRDTO;
import br.org.gam.api.location.application.useCases.CreateLocation.CreateLocation;
import br.org.gam.api.location.application.useCases.CreateLocation.CreateLocationDTO;
import br.org.gam.api.location.application.useCases.CreateLocation.CreateLocationRDTO;
import br.org.gam.api.location.application.useCases.GetLocation;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/location")
public class LocationController {

    private final CreateLocation createLocation;
    private final GetLocation getLocation;

    public LocationController(CreateLocation createLocation, GetLocation getLocation) {
        this.createLocation = createLocation;
        this.getLocation = getLocation;
    }

    @PostMapping
    public ResponseEntity<CreateLocationRDTO> createLocation(@RequestBody @Valid CreateLocationDTO dto) {
        CreateLocationRDTO responseDTO = createLocation.create(dto);

        URI httpLocation  = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(responseDTO.id())
                .toUri();

        return ResponseEntity.created(httpLocation).body(responseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LocationRDTO> getLocationById(@PathVariable UUID id) {
        LocationRDTO responseDTO = getLocation.byId(id);
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping
    public ResponseEntity<Page<LocationRDTO>> getAllLocations(Pageable pageable) {
        return ResponseEntity.ok(
                getLocation.all(pageable)
        );
    }
}
