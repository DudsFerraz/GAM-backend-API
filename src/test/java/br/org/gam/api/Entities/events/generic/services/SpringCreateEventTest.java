package br.org.gam.api.Entities.events.generic.services;

import br.org.gam.api.Entities.RBAC.permission.Permission;
import br.org.gam.api.Entities.RBAC.permission.services.getPermissionInstance.GetPermissionInstance;
import br.org.gam.api.Entities.events.generic.Event;
import br.org.gam.api.Entities.events.generic.EventMapper;
import br.org.gam.api.Entities.events.generic.EventStatus;
import br.org.gam.api.Entities.events.generic.EventType;
import br.org.gam.api.Entities.events.generic.persistence.EventEntity;
import br.org.gam.api.Entities.events.generic.persistence.EventRepository;
import br.org.gam.api.Entities.events.generic.services.createEvent.CreateEventDTO;
import br.org.gam.api.Entities.events.generic.services.createEvent.CreateEventRDTO;
import br.org.gam.api.Entities.events.generic.services.createEvent.SpringCreateEvent;
import br.org.gam.api.Entities.location.Location;
import br.org.gam.api.Entities.location.services.getLocationInstance.GetLocationInstance;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Create Event Use Case")
class SpringCreateEventTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private GetLocationInstance getLocationInstanceService;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private GetPermissionInstance getPermissionInstance;

    @InjectMocks
    private SpringCreateEvent createEvent;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid event data -> event is created")
        void validEventDataShouldCreateEvent() {
            UUID locationId = UUID.randomUUID();
            UUID permissionId = UUID.randomUUID();
            Instant beginDate = Instant.now().plusSeconds(3600);
            Instant endDate = beginDate.plusSeconds(3600);
            CreateEventDTO dto = new CreateEventDTO("  Sunday Mass  ", null, locationId, permissionId, beginDate, endDate, EventType.MISSA);
            Location location = mock(Location.class);
            Permission permission = mock(Permission.class);
            EventEntity mappedEntity = new EventEntity();
            EventEntity savedEntity = new EventEntity();
            CreateEventRDTO expectedResponse = new CreateEventRDTO(UUID.randomUUID());

            when(getLocationInstanceService.domainById(locationId)).thenReturn(location);
            when(getPermissionInstance.domainById(permissionId)).thenReturn(permission);
            when(eventMapper.domainToEntity(any(Event.class))).thenReturn(mappedEntity);
            when(eventRepository.save(mappedEntity)).thenReturn(savedEntity);
            when(eventMapper.entityToCreateEventRDTO(savedEntity)).thenReturn(expectedResponse);

            CreateEventRDTO response = createEvent.create(dto);

            assertThat(response).isSameAs(expectedResponse);

            ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
            verify(eventMapper).domainToEntity(eventCaptor.capture());
            Event event = eventCaptor.getValue();
            assertThat(event.getId()).isNotNull();
            assertThat(event.getId().version()).isEqualTo(7);
            assertThat(event.getTitle()).isEqualTo("Sunday Mass");
            assertThat(event.getDescription()).isEmpty();
            assertThat(event.getLocation()).isSameAs(location);
            assertThat(event.getRequiredPermission()).isSameAs(permission);
            assertThat(event.getBeginDate()).isEqualTo(beginDate);
            assertThat(event.getEndDate()).isEqualTo(endDate);
            assertThat(event.getType()).isEqualTo(EventType.MISSA);
            assertThat(event.getStatus()).isEqualTo(EventStatus.SCHEDULED);
            verify(eventRepository).save(mappedEntity);
        }
    }
}
