package br.org.gam.api.event.Oratorio.application;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.Oratorio.application.useCases.CreateOratorio.CreateOratorioRDTO;
import br.org.gam.api.event.Oratorio.domain.Oratorio;
import br.org.gam.api.event.Oratorio.persistence.OratorioEntity;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.oratoriano.application.OratorianoMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {EventMapper.class, MemberMapper.class, OratorianoMapper.class})
public interface OratorioMapper {
    OratorioEntity domainToEntity(Oratorio domain);
    Oratorio entityToDomain(OratorioEntity entity);
    OratorioRDTO entityToRDTO(OratorioEntity entity);
    CreateOratorioRDTO entityToCreateOratorioRDTO(OratorioEntity entity);
}
