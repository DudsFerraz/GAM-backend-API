package br.org.gam.api.event.oratorio.application.useCases.createOratorio;

import br.org.gam.api.event.application.useCases.createEvent.CreateEventDTO;
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
