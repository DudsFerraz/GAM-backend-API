package br.org.gam.api.event.Missa.domain;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

@UnitTest
@DisplayName("Missa Aggregate")
class MissaTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid event and assignments -> missa with event identity")
        void validEventAndAssignmentsShouldCreateMissaWithEventIdentity() {
            Event event = event();
            Member comentarios = mock(Member.class);
            Member leitura1 = mock(Member.class);
            Member salmo = mock(Member.class);
            Member leitura2 = mock(Member.class);
            Member preces = mock(Member.class);
            Member acolhida = mock(Member.class);

            Missa missa = Missa.register(event, comentarios, leitura1, salmo, leitura2, preces, Set.of(acolhida));

            assertThat(missa.getId()).isEqualTo(event.getId());
            assertThat(missa.getEvent()).isSameAs(event);
            assertThat(missa.getComentariosMember()).isSameAs(comentarios);
            assertThat(missa.getLeitura1Member()).isSameAs(leitura1);
            assertThat(missa.getSalmoMember()).isSameAs(salmo);
            assertThat(missa.getLeitura2Member()).isSameAs(leitura2);
            assertThat(missa.getPrecesMember()).isSameAs(preces);
            assertThat(missa.getAcolhidaMembers()).containsExactly(acolhida);
        }

        @Test
        @DisplayName("EP - null acolhida members -> empty acolhida set")
        void nullAcolhidaMembersShouldCreateEmptyAcolhidaSet() {
            Missa missa = Missa.register(event(), null, null, null, null, null, null);

            assertThat(missa.getAcolhidaMembers()).isEmpty();
        }

        @Test
        @DisplayName("EP - null event -> validation error")
        void nullEventShouldReturnValidationError() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Missa.register(null, null, null, null, null, null, null))
                    .withMessage("event cannot be null");
        }

        @Test
        @DisplayName("EP - remove assignment -> assignment is cleared")
        void removeAssignmentShouldClearAssignment() {
            Member member = mock(Member.class);
            Missa missa = Missa.register(event(), member, member, member, member, member, Set.of());

            missa.removeComentariosMember();
            missa.removeLeitura1Member();
            missa.removeSalmoMember();
            missa.removeLeitura2Member();
            missa.removePrecesMember();

            assertThat(missa.getComentariosMember()).isNull();
            assertThat(missa.getLeitura1Member()).isNull();
            assertThat(missa.getSalmoMember()).isNull();
            assertThat(missa.getLeitura2Member()).isNull();
            assertThat(missa.getPrecesMember()).isNull();
        }

        @Test
        @DisplayName("EP - set assignment -> assignment is updated")
        void setAssignmentShouldUpdateAssignment() {
            Missa missa = Missa.register(event(), null, null, null, null, null, Set.of());
            Member member = mock(Member.class);

            missa.setComentariosMember(member);
            missa.setLeitura1Member(member);
            missa.setSalmoMember(member);
            missa.setLeitura2Member(member);
            missa.setPrecesMember(member);

            assertThat(missa.getComentariosMember()).isSameAs(member);
            assertThat(missa.getLeitura1Member()).isSameAs(member);
            assertThat(missa.getSalmoMember()).isSameAs(member);
            assertThat(missa.getLeitura2Member()).isSameAs(member);
            assertThat(missa.getPrecesMember()).isSameAs(member);
        }
    }

    private static Event event() {
        Instant beginDate = Instant.now().plusSeconds(3600);
        return Event.register("Missa", null, beginDate, beginDate.plusSeconds(3600), EventType.MISSA);
    }
}
