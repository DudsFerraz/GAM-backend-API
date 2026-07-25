package br.org.gam.api.oratoriano.domain;

import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@DisplayName("Oratoriano Aggregate")
class OratorianoTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid registration data -> preserves GamName and GamPhoneNumber")
        void validRegistrationDataShouldPreserveCommonPrimitives() {
            GamName name = new GamName("Ana", "Silva");
            GamPhoneNumber phoneNumber = GamPhoneNumber.fromString("+5519998877665");
            LocalDate birthDate = LocalDate.of(2015, 1, 10);

            Oratoriano oratoriano = Oratoriano.register(name, birthDate, phoneNumber);

            assertThat(oratoriano.getName()).isSameAs(name);
            assertThat(oratoriano.getBirthDate()).isEqualTo(birthDate);
            assertThat(oratoriano.getPhoneNumber()).isSameAs(phoneNumber);
        }

        @Test
        @DisplayName("REQ-ORATORIANO-007 - newly registered aggregate exposes all six zero attendance derivations")
        void newAggregateShouldExposeAllAttendanceDerivationsWithZeroCounts() throws Exception {
            Oratoriano oratoriano = Oratoriano.register(
                    new GamName("Ana", "Silva"),
                    LocalDate.of(2015, 1, 10),
                    null
            );

            assertDerivedCount(oratoriano, "oratorioAttendances", new Object[0]);
            assertDerivedCount(oratoriano, "oratorioYearAttendances", new Object[]{2026});
            assertDerivedCount(oratoriano, "oratorioMonthAttendances", new Object[]{2026, 7});
            assertDerivedCount(oratoriano, "oratorioDistinctMonthsAttendances", new Object[0]);
            assertDerivedCount(
                    oratoriano,
                    "oratorioYearDistinctMonthsAttendances",
                    new Object[]{2026}
            );
            assertDerivedCount(oratoriano, "oratorioDistinctYearsAttendances", new Object[0]);
        }

        private void assertDerivedCount(
                Oratoriano oratoriano,
                String methodName,
                Object[] arguments
        ) throws Exception {
            Method method = Arrays.stream(Oratoriano.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .filter(candidate -> candidate.getParameterCount() == arguments.length)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Missing required Oratoriano method " + methodName
                    ));
            assertThat(method.invoke(oratoriano, arguments))
                    .as(methodName + " for an aggregate without attendance")
                    .isInstanceOf(Number.class)
                    .extracting(value -> ((Number) value).longValue())
                    .isEqualTo(0L);
        }
    }
}
