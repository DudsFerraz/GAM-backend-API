package br.org.gam.api.oratoriano.application;

import br.org.gam.api.oratoriano.domain.Oratoriano;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.shared.auditing.IgnoreFullAuditFields;
import br.org.gam.api.shared.domain.Name;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface OratorianoMapper {
    @IgnoreFullAuditFields
    OratorianoEntity domainToEntity(Oratoriano oratorianoDomain);

    Oratoriano entityToDomain(OratorianoEntity oratorianoEntity);

    @Named("nameToString")
    default String nameToString(Name name) {
        if (name == null) return null;
        return name.toString();
    }

    @Mapping(target = "name", source = "oratorianoEntity.name", qualifiedByName = "nameToString")
    OratorianoRDTO entityToRDTO(OratorianoEntity oratorianoEntity);
}
