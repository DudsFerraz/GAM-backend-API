package br.org.gam.api.gamLocation.application;

import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GamLocationMapper {

    GamLocationRDTO entityToRDTO(GamLocationEntity entity);
}
