package br.org.gam.api.location.application;

import br.org.gam.api.location.application.useCases.CreateLocation.CreateLocationRDTO;
import br.org.gam.api.location.persistence.LocationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LocationMapper {

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    CreateLocationRDTO entityToCreateLocationRDTO(LocationEntity locationEntity);

    LocationRDTO entityToRDTO(LocationEntity locationEntity);
}
