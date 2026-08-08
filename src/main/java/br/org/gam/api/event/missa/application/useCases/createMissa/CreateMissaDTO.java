package br.org.gam.api.event.missa.application.useCases.createMissa;

import br.org.gam.api.event.application.useCases.createEvent.CreateEventDTO;
import java.util.Set;
import java.util.UUID;

public record CreateMissaDTO(
        CreateEventDTO event,
        UUID comentariosMemberId,
        UUID leitura1MemberId,
        UUID salmoMemberId,
        UUID leitura2MemberId,
        UUID precesMemberId,
        Set<UUID> acolhidaMembersIds
) {
}
