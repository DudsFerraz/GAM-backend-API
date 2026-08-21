package br.org.gam.api.event.oratorio.domain;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.oratoriano.domain.Oratoriano;
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
@DisplayName("Oratorio Aggregate")
class OratorioTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid event and assignments -> oratorio with event identity")
        void validEventAndAssignmentsShouldCreateOratorioWithEventIdentity() {
            Event event = event();
            Member lanche = mock(Member.class);
            Member jovens = mock(Member.class);
            Member criancas = mock(Member.class);
            Oratoriano oratoriano = mock(Oratoriano.class);

            Oratorio oratorio = Oratorio.register(event, Set.of(lanche), Set.of(jovens), Set.of(criancas), Set.of(oratoriano));

            assertThat(oratorio.getId()).isEqualTo(event.getId());
            assertThat(oratorio.getEvent()).isSameAs(event);
            assertThat(oratorio.getCancellationReason()).isNull();
            assertThat(oratorio.getLancheMembers()).containsExactly(lanche);
            assertThat(oratorio.getBtJovensMembers()).containsExactly(jovens);
            assertThat(oratorio.getBtCriancasMembers()).containsExactly(criancas);
            assertThat(oratorio.getOratorianos()).containsExactly(oratoriano);
        }

        @Test
        @DisplayName("EP - null assignment sets -> empty sets")
        void nullAssignmentSetsShouldCreateEmptySets() {
            Oratorio oratorio = Oratorio.register(event(), null, null, null, null);

            assertThat(oratorio.getLancheMembers()).isEmpty();
            assertThat(oratorio.getBtJovensMembers()).isEmpty();
            assertThat(oratorio.getBtCriancasMembers()).isEmpty();
            assertThat(oratorio.getOratorianos()).isEmpty();
        }

        @Test
        @DisplayName("EP - null event -> validation error")
        void nullEventShouldReturnValidationError() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Oratorio.register(null, null, null, null, null))
                    .withMessage("event cannot be null");
        }

        @Test
        @DisplayName("EP - add and remove assignments -> assignment sets are updated")
        void addAndRemoveAssignmentsShouldUpdateAssignmentSets() {
            Oratorio oratorio = Oratorio.register(event(), null, null, null, null);
            Member lanche = mock(Member.class);
            Member jovens = mock(Member.class);
            Member criancas = mock(Member.class);
            Oratoriano oratoriano = mock(Oratoriano.class);

            oratorio.addLancheMember(lanche);
            oratorio.addBtJovensMember(jovens);
            oratorio.addBtCriancasMember(criancas);
            oratorio.addOratoriano(oratoriano);

            assertThat(oratorio.getLancheMembers()).containsExactly(lanche);
            assertThat(oratorio.getBtJovensMembers()).containsExactly(jovens);
            assertThat(oratorio.getBtCriancasMembers()).containsExactly(criancas);
            assertThat(oratorio.getOratorianos()).containsExactly(oratoriano);

            oratorio.removeLancheMember(lanche);
            oratorio.removeBtJovensMember(jovens);
            oratorio.removeBtCriancasMember(criancas);
            oratorio.removeOratoriano(oratoriano);

            assertThat(oratorio.getLancheMembers()).isEmpty();
            assertThat(oratorio.getBtJovensMembers()).isEmpty();
            assertThat(oratorio.getBtCriancasMembers()).isEmpty();
            assertThat(oratorio.getOratorianos()).isEmpty();
        }
    }

    private static Event event() {
        Instant beginDate = Instant.now().plusSeconds(3600);
        return Event.register("Oratorio", null, beginDate, beginDate.plusSeconds(3600), EventType.ORATORIO);
    }
}
