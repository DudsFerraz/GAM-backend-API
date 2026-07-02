package br.org.gam.api.presence.application;

import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.member.application.MemberRDTO;
import java.util.UUID;

public record PresenceRDTO(
        UUID id,
        MemberRDTO member,
        EventRDTO event,
        String observations
) {
}
