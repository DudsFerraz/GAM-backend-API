package br.org.gam.api.event.web;

import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.event.application.useCases.createEvent.CreateEvent;
import br.org.gam.api.event.application.useCases.createEvent.CreateGenericEventDTO;
import br.org.gam.api.event.application.useCases.GetEvent;
import br.org.gam.api.event.application.useCases.SearchEvents;
import br.org.gam.api.event.application.useCases.manageEvent.EventReasonDTO;
import br.org.gam.api.event.application.useCases.manageEvent.EventReplacementDTO;
import br.org.gam.api.event.application.useCases.manageEvent.ManageGenericEvent;
import br.org.gam.api.event.application.useCases.manageEvent.ReopenEventDTO;
import br.org.gam.api.presence.application.PresenceRDTO;
import br.org.gam.api.presence.application.useCases.GetPresence;
import br.org.gam.api.presence.application.useCases.managePresence.RemovePresenceDTO;
import br.org.gam.api.presence.application.useCases.managePresence.UpdatePresenceObservationsDTO;
import br.org.gam.api.presence.application.useCases.registerPresence.RegisterPresence;
import br.org.gam.api.presence.application.useCases.registerPresence.RegisterPresenceDTO;
import br.org.gam.api.presence.application.useCases.registerPresence.RegisterPresenceRDTO;
import br.org.gam.api.presence.application.useCases.registerPresence.RegisterPresenceRequestDTO;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.web.PagedResponse;
import br.org.gam.api.shared.web.PublicApiUri;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    private final ManageGenericEvent manageGenericEvent;
    private final RegisterPresence registerPresence;

    public EventController(CreateEvent createEvent,
                           GetEvent getEvent,
                           SearchEvents searchEvent,
                           GetPresence getPresence,
                           ManageGenericEvent manageGenericEvent,
                           RegisterPresence registerPresence) {

        this.createEvent = createEvent;
        this.getEvent = getEvent;
        this.searchEvent = searchEvent;
        this.getPresence = getPresence;
        this.manageGenericEvent = manageGenericEvent;
        this.registerPresence = registerPresence;
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_CREATE + "')")
    @Operation(operationId = "createEvent")
    @ApiResponse(
            responseCode = "201",
            description = "Event created",
            headers = @Header(
                    name = "Location",
                    description = "URI of the created Event",
                    schema = @Schema(type = "string", format = "uri")
            )
    )
    @PostMapping
    public ResponseEntity<EventRDTO> createEvent(@RequestBody @Valid CreateGenericEventDTO dto){

        EventRDTO responseDTO = createEvent.create(dto);

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

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_MANAGE + "')")
    @Operation(operationId = "replaceGenericEvent")
    @PutMapping("/{id}")
    public ResponseEntity<EventRDTO> replaceEvent(@PathVariable UUID id,
                                                   @RequestBody @Valid EventReplacementDTO dto) {
        return ResponseEntity.ok(manageGenericEvent.replace(id, dto));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_MANAGE + "')")
    @Operation(operationId = "lockGenericEvent")
    @PatchMapping("/{id}/lock")
    public ResponseEntity<EventRDTO> lockEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(manageGenericEvent.lock(id));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_MANAGE + "')")
    @Operation(operationId = "finalizeGenericEvent")
    @PatchMapping("/{id}/finalize")
    public ResponseEntity<EventRDTO> finalizeEvent(@PathVariable UUID id) {
        return ResponseEntity.ok(manageGenericEvent.finalizeEvent(id));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_MANAGE + "')")
    @Operation(operationId = "reopenGenericEvent")
    @PatchMapping("/{id}/reopen")
    public ResponseEntity<EventRDTO> reopenEvent(@PathVariable UUID id,
                                                  @RequestBody @Valid ReopenEventDTO dto) {
        return ResponseEntity.ok(manageGenericEvent.reopen(id, dto));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_MANAGE + "')")
    @Operation(operationId = "cancelGenericEvent")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<EventRDTO> cancelEvent(@PathVariable UUID id,
                                                  @RequestBody @Valid EventReasonDTO dto) {
        return ResponseEntity.ok(manageGenericEvent.cancel(id, dto));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_MANAGE + "')")
    @Operation(operationId = "deleteGenericEvent")
    @ApiResponse(responseCode = "204", description = "Event deleted", content = @Content)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID id,
                                             @RequestBody @Valid EventReasonDTO dto) {
        manageGenericEvent.delete(id, dto);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.PRESENCE_REGISTER + "')")
    @Operation(operationId = "registerEventPresence")
    @ApiResponse(
            responseCode = "201",
            description = "Presence registered",
            headers = @Header(
                    name = "Location",
                    description = "URI of the registered Presence",
                    schema = @Schema(type = "string", format = "uri")
            )
    )
    @PostMapping("/{eventId}/presences")
    public ResponseEntity<RegisterPresenceRDTO> registerPresence(
            @PathVariable UUID eventId,
            @RequestBody @Valid RegisterPresenceRequestDTO dto
    ) {
        RegisterPresenceRDTO result = registerPresence.register(
                new RegisterPresenceDTO(eventId, dto.memberId(), dto.observations())
        );
        return ResponseEntity.created(
                PublicApiUri.forResource("/events/" + eventId + "/presences/" + dto.memberId())
        ).body(result);
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_GET_PRESENCES + "')")
    @Operation(operationId = "getEventPresences")
    @GetMapping("/{eventId}/presences")
    public ResponseEntity<PagedResponse<PresenceRDTO>> getEventPresences(
            @PathVariable UUID eventId,
            @Parameter(
                    description = "Trimmed, case-insensitive, accent-sensitive literal substring matched "
                            + "separately against Member firstName and surname. Blank values return 400."
            )
            @RequestParam(required = false) String name,
            Pageable pageable
    ){

        return ResponseEntity.ok(PagedResponse.from(getPresence.allByEvent(eventId, name, pageable)));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.EVENT_GET_PRESENCES + "')")
    @Operation(operationId = "getEventPresence")
    @GetMapping("/{eventId}/presences/{memberId}")
    public ResponseEntity<PresenceRDTO> getEventPresence(
            @PathVariable UUID eventId,
            @PathVariable UUID memberId
    ) {
        return ResponseEntity.ok(getPresence.byEventAndMember(eventId, memberId));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.PRESENCE_EDIT + "')")
    @Operation(operationId = "updateEventPresenceObservations")
    @PatchMapping("/{eventId}/presences/{memberId}")
    public ResponseEntity<PresenceRDTO> updateEventPresenceObservations(
            @PathVariable UUID eventId,
            @PathVariable UUID memberId,
            @RequestBody @Valid UpdatePresenceObservationsDTO dto
    ) {
        return ResponseEntity.ok(getPresence.updateObservations(eventId, memberId, dto));
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.PRESENCE_REMOVE + "')")
    @Operation(operationId = "removeEventPresence")
    @ApiResponse(responseCode = "204", description = "Presence removed", content = @Content)
    @DeleteMapping("/{eventId}/presences/{memberId}")
    public ResponseEntity<Void> removeEventPresence(
            @PathVariable UUID eventId,
            @PathVariable UUID memberId,
            @RequestBody @Valid RemovePresenceDTO dto
    ) {
        getPresence.remove(eventId, memberId, dto);
        return ResponseEntity.noContent().build();
    }
}
