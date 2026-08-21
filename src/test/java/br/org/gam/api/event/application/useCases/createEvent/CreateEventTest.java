package br.org.gam.api.event.application.useCases.createEvent;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.gamLocation.application.GamLocationEntityLoader;
import br.org.gam.api.gamLocation.persistence.GamLocationEntity;
import br.org.gam.api.rbac.permission.application.PermissionEntityLoader;
import br.org.gam.api.rbac.permission.persistence.PermissionEntity;
import br.org.gam.api.shared.activitylog.ActivityEvents;
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
class CreateEventTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private GamLocationEntityLoader gamLocationEntityLoader;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private PermissionEntityLoader getPermissionInstance;

    @Mock
    private ActivityEvents activityEvents;

    @InjectMocks
    private CreateEvent createEvent;

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
            GamLocationEntity location = new GamLocationEntity();
            PermissionEntity permission = new PermissionEntity();
            EventEntity mappedEntity = new EventEntity();
            EventEntity savedEntity = new EventEntity();
            savedEntity.setTitle("Sunday Mass");
            CreateEventRDTO expectedResponse = new CreateEventRDTO(UUID.randomUUID());

            when(gamLocationEntityLoader.requiredByIdForUpdate(locationId)).thenReturn(location);
            when(getPermissionInstance.requiredById(permissionId)).thenReturn(permission);
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
            verify(activityEvents).eventCreated(
                    event.getId(),
                    savedEntity.getTitle(),
                    event.getType(),
                    event.getStatus(),
                    locationId,
                    permissionId
            );
        }
    }
}
