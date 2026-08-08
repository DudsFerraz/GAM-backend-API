package br.org.gam.api.event.oratorio.application;

import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.oratoriano.application.OratorianoRDTO;
import java.util.Set;
import java.util.UUID;

public record OratorioRDTO(
        UUID id,
        EventRDTO event,
        String cancellationReason,
        Set<MemberRDTO> lancheMembers,
        Set<MemberRDTO> btJovensMembers,
        Set<MemberRDTO> btCriancasMembers,
        Set<OratorianoRDTO> oratorianos
) {
}
