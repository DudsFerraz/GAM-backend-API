package br.org.gam.api.location.application.useCases.GetLocation;

import br.org.gam.api.location.application.LocationRDTO;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetLocation {
    LocationRDTO byId(UUID id);
    Page<LocationRDTO> all(Pageable pageable);
}
