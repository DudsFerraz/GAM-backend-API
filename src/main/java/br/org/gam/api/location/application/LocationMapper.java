package br.org.gam.api.location.application;

import br.org.gam.api.location.application.useCases.CreateLocation.CreateLocationRDTO;
import br.org.gam.api.location.domain.Location;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.shared.auditing.IgnoreFullAuditFields;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LocationMapper {
    @IgnoreFullAuditFields
    LocationEntity domainToEntity(Location locationDomain);

    Location entityToDomain(LocationEntity locationEntity);

    CreateLocationRDTO entityToCreateLocationRDTO(LocationEntity locationEntity);

    LocationRDTO entityToLocationRDTO(LocationEntity locationEntity);
}
