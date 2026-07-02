package br.org.gam.api.event.application.useCases.CreateEvent;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.location.application.useCases.GetLocationInstance.GetLocationInstance;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.rbac.Permission.application.useCases.GetPermissionInstance.GetPermissionInstance;
import br.org.gam.api.rbac.Permission.persistence.PermissionEntity;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
            LocationEntity location = new LocationEntity();
            PermissionEntity permission = new PermissionEntity();
            EventEntity mappedEntity = new EventEntity();
            EventEntity savedEntity = new EventEntity();
            CreateEventRDTO expectedResponse = new CreateEventRDTO(UUID.randomUUID());

            when(getLocationInstanceService.entityById(locationId)).thenReturn(location);
            when(getPermissionInstance.entityById(permissionId)).thenReturn(permission);
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
            assertThat(event.getBeginDate()).isEqualTo(beginDate);
            assertThat(event.getEndDate()).isEqualTo(endDate);
            assertThat(event.getType()).isEqualTo(EventType.MISSA);
            assertThat(event.getStatus()).isEqualTo(EventStatus.SCHEDULED);
            assertThat(mappedEntity.getLocation()).isSameAs(location);
            assertThat(mappedEntity.getRequiredPermission()).isSameAs(permission);
            verify(eventRepository).save(mappedEntity);
        }
    }
}
