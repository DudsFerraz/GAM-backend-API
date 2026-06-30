package br.org.gam.api.Entities.events.generic;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@DisplayName("Event Aggregate")
class EventTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - future event data -> scheduled event with generated identity")
        void futureEventDataShouldCreateScheduledEventWithGeneratedIdentity() {
            Instant beginDate = Instant.now().plusSeconds(3600);
            Instant endDate = beginDate.plusSeconds(3600);

            Event event = Event.register("  Sunday Mass  ", "  Main celebration  ", null, null, beginDate, endDate, EventType.MISSA);

            assertThat(event.getId()).isNotNull();
            assertThat(event.getId().version()).isEqualTo(7);
            assertThat(event.getTitle()).isEqualTo("Sunday Mass");
            assertThat(event.getDescription()).isEqualTo("Main celebration");
            assertThat(event.getBeginDate()).isEqualTo(beginDate);
            assertThat(event.getEndDate()).isEqualTo(endDate);
            assertThat(event.getType()).isEqualTo(EventType.MISSA);
            assertThat(event.getStatus()).isEqualTo(EventStatus.SCHEDULED);
        }

        @Test
        @DisplayName("EP - past event data -> completed event")
        void pastEventDataShouldCreateCompletedEvent() {
            Instant beginDate = Instant.now().minusSeconds(7200);
            Instant endDate = Instant.now().minusSeconds(3600);

            Event event = Event.register("Past event", null, null, null, beginDate, endDate, EventType.ORATORIO);

            assertThat(event.getDescription()).isEmpty();
            assertThat(event.getStatus()).isEqualTo(EventStatus.COMPLETED);
        }

        @Test
        @DisplayName("BVA - end date equal to begin date -> validation error")
        void endDateEqualToBeginDateShouldReturnValidationError() {
            Instant date = Instant.now().plusSeconds(3600);

            assertThatThrownBy(() -> Event.register("Event", null, null, null, date, date, EventType.MISSA))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("endDate must be after beginDate.");
        }

        @Test
        @DisplayName("BVA - end date before begin date -> validation error")
        void endDateBeforeBeginDateShouldReturnValidationError() {
            Instant beginDate = Instant.now().plusSeconds(3600);
            Instant endDate = beginDate.minusSeconds(1);

            assertThatThrownBy(() -> Event.register("Event", null, null, null, beginDate, endDate, EventType.MISSA))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("endDate must be after beginDate.");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null title -> validation error")
        void nullTitleShouldReturnValidationError(String title) {
            Instant beginDate = Instant.now().plusSeconds(3600);
            Instant endDate = beginDate.plusSeconds(3600);

            assertThatNullPointerException()
                    .isThrownBy(() -> Event.register(title, null, null, null, beginDate, endDate, EventType.MISSA))
                    .withMessage("Title cannot be null");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null begin date -> validation error")
        void nullBeginDateShouldReturnValidationError(Instant beginDate) {
            Instant endDate = Instant.now().plusSeconds(3600);

            assertThatNullPointerException()
                    .isThrownBy(() -> Event.register("Event", null, null, null, beginDate, endDate, EventType.MISSA))
                    .withMessage("Begin date cannot be null");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null end date -> validation error")
        void nullEndDateShouldReturnValidationError(Instant endDate) {
            Instant beginDate = Instant.now().plusSeconds(3600);

            assertThatNullPointerException()
                    .isThrownBy(() -> Event.register("Event", null, null, null, beginDate, endDate, EventType.MISSA))
                    .withMessage("End date cannot be null");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null event type -> validation error")
        void nullEventTypeShouldReturnValidationError(EventType type) {
            Instant beginDate = Instant.now().plusSeconds(3600);
            Instant endDate = beginDate.plusSeconds(3600);

            assertThatNullPointerException()
                    .isThrownBy(() -> Event.register("Event", null, null, null, beginDate, endDate, type))
                    .withMessage("Event type cannot be null");
        }

        @Test
        @DisplayName("EP - cancel event -> cancelled status")
        void cancelEventShouldSetCancelledStatus() {
            Instant beginDate = Instant.now().plusSeconds(3600);
            Instant endDate = beginDate.plusSeconds(3600);
            Event event = Event.register("Event", null, null, null, beginDate, endDate, EventType.MISSA);

            event.cancel();

            assertThat(event.getStatus()).isEqualTo(EventStatus.CANCELLED);
        }
    }
}
