package br.org.gam.api.event.application.useCases.SearchEvents;

import br.org.gam.api.event.application.EventMapper;
import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.shared.specification.ComparationMethods;
import br.org.gam.api.shared.specification.SpecificationFilter;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Search Events Use Case")
@SuppressWarnings("unchecked")
class SpringSearchEventsTest {

    @Mock
    private EventRepository eventRepo;

    @Mock
    private EventMapper eventMapper;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private SpringSearchEvents searchEvents;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid filters and pageable -> mapped event page")
        void validFiltersAndPageableShouldReturnMappedEventPage() {
            List<SpecificationFilter> filters = List.of(
                    new SpecificationFilter("type", EventType.MISSA, ComparationMethods.EQUALS)
            );
            Pageable pageable = PageRequest.of(0, 10);
            EventEntity firstEntity = new EventEntity();
            EventEntity secondEntity = new EventEntity();
            EventRDTO firstResponse = response(UUID.randomUUID(), EventType.MISSA);
            EventRDTO secondResponse = response(UUID.randomUUID(), EventType.ORATORIO);

            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of("EVENTS_SEARCH"));
            when(eventRepo.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(firstEntity, secondEntity), pageable, 2));
            when(eventMapper.entityToEventRDTO(firstEntity)).thenReturn(firstResponse);
            when(eventMapper.entityToEventRDTO(secondEntity)).thenReturn(secondResponse);

            Page<EventRDTO> response = searchEvents.search(filters, pageable);

            assertThat(response.getContent()).containsExactly(firstResponse, secondResponse);
            assertThat(response.getTotalElements()).isEqualTo(2);

            ArgumentCaptor<Specification<EventEntity>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
            verify(eventRepo).findAll(specificationCaptor.capture(), eq(pageable));
            assertThat(specificationCaptor.getValue()).isNotNull();
            verify(securityUtils).getLoggedUserAuthorities();
            verify(eventMapper).entityToEventRDTO(firstEntity);
            verify(eventMapper).entityToEventRDTO(secondEntity);
        }

        @Test
        @DisplayName("EP - empty filters -> mapped event page")
        void emptyFiltersShouldReturnMappedEventPage() {
            Pageable pageable = PageRequest.of(0, 10);
            EventEntity entity = new EventEntity();
            EventRDTO expectedResponse = response(UUID.randomUUID(), EventType.MISSA);

            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of("EVENTS_SEARCH"));
            when(eventRepo.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));
            when(eventMapper.entityToEventRDTO(entity)).thenReturn(expectedResponse);

            Page<EventRDTO> response = searchEvents.search(List.of(), pageable);

            assertThat(response.getContent()).containsExactly(expectedResponse);
            verify(securityUtils).getLoggedUserAuthorities();
            verify(eventRepo).findAll(any(Specification.class), eq(pageable));
            verify(eventMapper).entityToEventRDTO(entity);
        }

        @Test
        @DisplayName("EP - invalid filter value -> filter is ignored")
        void invalidFilterValueShouldBeIgnored() {
            Pageable pageable = PageRequest.of(0, 10);
            List<SpecificationFilter> filters = List.of(
                    new SpecificationFilter("title", " ", ComparationMethods.EQUALS)
            );

            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of("EVENTS_SEARCH"));
            when(eventRepo.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(Page.empty(pageable));

            Page<EventRDTO> response = searchEvents.search(filters, pageable);

            assertThat(response.getContent()).isEmpty();
            verify(securityUtils).getLoggedUserAuthorities();
            verify(eventRepo).findAll(any(Specification.class), eq(pageable));
            verifyNoInteractions(eventMapper);
        }
    }

    private static EventRDTO response(UUID id, EventType type) {
        return new EventRDTO(
                id,
                "Event",
                "",
                null,
                null,
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200),
                type,
                EventStatus.SCHEDULED
        );
    }
}
