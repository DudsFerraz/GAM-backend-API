package br.org.gam.api.event.Oratorio.application.useCases.CreateOratorio;

import br.org.gam.api.event.application.useCases.CreateEvent.CreateEventDTO;
import br.org.gam.api.event.Oratorio.domain.Oratorio;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.oratoriano.domain.Oratoriano;
import java.util.Set;
import java.util.UUID;

public record CreateOratorioDTO(
        CreateEventDTO event,
        Set<UUID> lancheMembersIds,
        Set<UUID> btJovensMembersIds,
        Set<UUID> btCriancasMembersIds,
        Set<UUID> oratorianosIds
) {
}
