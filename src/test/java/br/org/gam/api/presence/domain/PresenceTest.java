package br.org.gam.api.presence.domain;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

@UnitTest
@DisplayName("Presence Aggregate")
class PresenceTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid member and event -> presence with generated identity")
        void validMemberAndEventShouldCreatePresenceWithGeneratedIdentity() {
            Member member = mock(Member.class);
            Event event = mock(Event.class);

            Presence presence = Presence.register(member, event, "  Arrived before opening prayer  ");

            assertThat(presence.getId()).isNotNull();
            assertThat(presence.getId().version()).isEqualTo(7);
            assertThat(presence.getMember()).isSameAs(member);
            assertThat(presence.getEvent()).isSameAs(event);
            assertThat(presence.getObservations()).isEqualTo("Arrived before opening prayer");
        }

        @Test
        @DisplayName("EP - null observations -> empty observations")
        void nullObservationsShouldCreateEmptyObservations() {
            Presence presence = Presence.register(mock(Member.class), mock(Event.class), null);

            assertThat(presence.getObservations()).isEmpty();
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null member -> validation error")
        void nullMemberShouldReturnValidationError(Member member) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Presence.register(member, mock(Event.class), null))
                    .withMessage("Present member must not be null");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null event -> validation error")
        void nullEventShouldReturnValidationError(Event event) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Presence.register(mock(Member.class), event, null))
                    .withMessage("Presence event must not be null");
        }
    }
}
