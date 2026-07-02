package br.org.gam.api.event.web;

import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.event.application.useCases.CreateEvent.CreateEvent;
import br.org.gam.api.event.application.useCases.CreateEvent.CreateEventDTO;
import br.org.gam.api.event.application.useCases.CreateEvent.CreateEventRDTO;
import br.org.gam.api.event.application.useCases.GetEvent;
import br.org.gam.api.event.application.useCases.SearchEvents;
import br.org.gam.api.presence.application.PresenceRDTO;
import br.org.gam.api.presence.application.useCases.GetPresence;
import br.org.gam.api.rbac.Permission.domain.PermissionEnum;
import br.org.gam.api.shared.specification.SearchDTO;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/events")
public class EventController {
    private final CreateEvent createEvent;
    private final GetEvent getEvent;
    private final SearchEvents searchEvent;
    private final GetPresence getPresence;

    public EventController(CreateEvent createEvent,
                           GetEvent getEvent,
                           SearchEvents searchEvent,
                           GetPresence getPresence) {

        this.createEvent = createEvent;
        this.getEvent = getEvent;
        this.searchEvent = searchEvent;
        this.getPresence = getPresence;
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_CREATE + "')")
    @PostMapping
    public ResponseEntity<CreateEventRDTO> createEvent(@RequestBody @Valid CreateEventDTO dto){

        CreateEventRDTO responseDTO = createEvent.create(dto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(responseDTO.id())
                .toUri();

        return ResponseEntity.created(location).body(responseDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventRDTO> getEventById(@PathVariable UUID id){

        EventRDTO responseDTO = getEvent.byId(id);
        return ResponseEntity.ok(responseDTO);
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_SEARCH + "')")
    @PostMapping("/search")
    public ResponseEntity<Page<EventRDTO>> searchEvents(@RequestBody @Valid SearchDTO searchDTO,
                                                        Pageable pageable){

        return ResponseEntity.ok(
                searchEvent.search(searchDTO, pageable)
        );
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_GET_PRESENCES + "')")
    @GetMapping("/{eventId}/presences")
    public ResponseEntity<Page<PresenceRDTO>> getEventPresences(@PathVariable UUID eventId,
                                                                Pageable pageable){

        return ResponseEntity.ok(
                getPresence.allByEvent(eventId, pageable)
        );
    }
}
