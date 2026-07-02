package br.org.gam.api.presence.application;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.presence.application.useCases.RegisterPresence.RegisterPresenceRDTO;
import br.org.gam.api.presence.persistence.PresenceEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {MemberMapper.class, EventMapper.class})
public interface PresenceMapper {
    RegisterPresenceRDTO entityToRegisterPresenceRDTO(PresenceEntity presenceEntity);
    PresenceRDTO entityToPresenceRDTO(PresenceEntity presenceEntity);
}
