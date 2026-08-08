package br.org.gam.api.event.oratorio.application;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.oratorio.application.useCases.createOratorio.CreateOratorioRDTO;
import br.org.gam.api.event.oratorio.domain.Oratorio;
import br.org.gam.api.event.oratorio.persistence.OratorioEntity;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.oratoriano.application.OratorianoMapper;
import br.org.gam.api.shared.auditing.IgnoreFullAuditFields;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {EventMapper.class, MemberMapper.class, OratorianoMapper.class})
public interface OratorioMapper {

    // =====================================================================================
    // Domain <-> Persistence
    // =====================================================================================

    @IgnoreFullAuditFields
    OratorioEntity domainToEntity(Oratorio domain);

    Oratorio entityToDomain(OratorioEntity entity);

    // =====================================================================================
    // Persistence -> RDTO
    // =====================================================================================

    OratorioRDTO entityToRDTO(OratorioEntity entity);

    CreateOratorioRDTO entityToCreateOratorioRDTO(OratorioEntity entity);
}
