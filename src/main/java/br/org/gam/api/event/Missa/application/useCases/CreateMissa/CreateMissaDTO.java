package br.org.gam.api.event.Missa.application.useCases.CreateMissa;

import br.org.gam.api.event.application.useCases.CreateEvent.CreateEventDTO;
import br.org.gam.api.event.Missa.domain.Missa;
import jakarta.validation.constraints.NotNull;
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
