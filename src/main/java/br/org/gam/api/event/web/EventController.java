package br.org.gam.api.event.web;

import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.event.application.useCases.createEvent.CreateEvent;
import br.org.gam.api.event.application.useCases.createEvent.CreateGenericEventDTO;
import br.org.gam.api.event.application.useCases.createEvent.CreateEventRDTO;
import br.org.gam.api.event.application.useCases.GetEvent;
import br.org.gam.api.event.application.useCases.SearchEvents;
import br.org.gam.api.presence.application.PresenceRDTO;
import br.org.gam.api.presence.application.useCases.GetPresence;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.web.PagedResponse;
import br.org.gam.api.shared.web.PublicApiUri;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @Operation(operationId = "createEvent")
    @PostMapping
    public ResponseEntity<CreateEventRDTO> createEvent(@RequestBody @Valid CreateGenericEventDTO dto){

        CreateEventRDTO responseDTO = createEvent.create(dto);

        return ResponseEntity.created(PublicApiUri.forResource("/events/" + responseDTO.id()))
                .body(responseDTO);
    }

    @Operation(operationId = "getEvent")
    @GetMapping("/{id}")
    public ResponseEntity<EventRDTO> getEventById(@PathVariable UUID id){

        EventRDTO responseDTO = getEvent.byId(id);
        return ResponseEntity.ok(responseDTO);
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_SEARCH + "')")
    @Operation(operationId = "searchEvents")
    @PostMapping("/search")
    public ResponseEntity<PagedResponse<EventRDTO>> searchEvents(@RequestBody @Valid SearchDTO searchDTO,
                                                                  Pageable pageable){

        return ResponseEntity.ok(PagedResponse.from(searchEvent.search(searchDTO, pageable)));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_GET_PRESENCES + "')")
    @Operation(operationId = "getEventPresences")
    @GetMapping("/{eventId}/presences")
    public ResponseEntity<PagedResponse<PresenceRDTO>> getEventPresences(@PathVariable UUID eventId,
                                                                           Pageable pageable){

        return ResponseEntity.ok(PagedResponse.from(getPresence.allByEvent(eventId, pageable)));
    }
}
