package br.org.gam.api.event.Missa.application;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.Missa.application.useCases.CreateMissa.CreateMissaRDTO;
import br.org.gam.api.event.Missa.domain.Missa;
import br.org.gam.api.event.Missa.persistence.MissaEntity;
import br.org.gam.api.member.application.MemberMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {EventMapper.class, MemberMapper.class})
public interface MissaMapper {
    MissaEntity domainToEntity(Missa domain);
    Missa entityToDomain(MissaEntity entity);
    MissaRDTO entityToRDTO(MissaEntity entity);
    CreateMissaRDTO entityToCreateMissaRDTO(MissaEntity entity);
}
