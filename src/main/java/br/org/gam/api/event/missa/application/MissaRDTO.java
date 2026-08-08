package br.org.gam.api.event.missa.application;

import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.member.application.MemberRDTO;
import java.util.Set;
import java.util.UUID;

public record MissaRDTO(
        UUID id,
        EventRDTO event,
        MemberRDTO comentariosMember,
        MemberRDTO leitura1Member,
        MemberRDTO salmoMember,
        MemberRDTO leitura2Member,
        MemberRDTO precesMember,
        Set<MemberRDTO> acolhidaMembers
) {
}
