package br.org.gam.api.shared.persistence;

import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.web.AccountController;
import br.org.gam.api.event.application.EventRDTO;
import br.org.gam.api.event.domain.Event;
import br.org.gam.api.event.domain.EventType;
import br.org.gam.api.event.persistence.EventEntity;
import br.org.gam.api.event.web.EventController;
import br.org.gam.api.location.application.LocationRDTO;
import br.org.gam.api.location.persistence.LocationEntity;
import br.org.gam.api.location.web.LocationController;
import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.web.MemberController;
import br.org.gam.api.oratoriano.domain.Oratoriano;
import br.org.gam.api.oratoriano.persistence.OratorianoEntity;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@UnitTest
@DisplayName("UUID Identity")
class UUIDIdentityTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - newly registered persisted domain resources -> UUID v7 identity")
        void newlyRegisteredPersistedDomainResourcesShouldReceiveUuidV7Identity() {
            Account account = account();
            Event event = Event.register(
                    "Oratorio",
                    "Saturday afternoon",
                    Instant.parse("2030-01-05T13:00:00Z"),
                    Instant.parse("2030-01-05T17:00:00Z"),
                    EventType.ORATORIO
            );
            Member member = Member.register(account, name(), LocalDate.of(1990, 1, 1), phoneNumber());
            Oratoriano oratoriano = Oratoriano.register(name(), LocalDate.of(2015, 1, 1), phoneNumber());

            assertThat(account.getId()).isNotNull().extracting(UUID::version).isEqualTo(7);
            assertThat(event.getId()).isNotNull().extracting(UUID::version).isEqualTo(7);
            assertThat(member.getId()).isNotNull().extracting(UUID::version).isEqualTo(7);
            assertThat(oratoriano.getId()).isNotNull().extracting(UUID::version).isEqualTo(7);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @ParameterizedTest
        @MethodSource("br.org.gam.api.shared.persistence.UUIDIdentityTest#persistedResourceTypes")
        @DisplayName("COND - persisted resource id field type -> UUID")
        void persistedResourceIdFieldShouldUseUuidType(Class<?> resourceType) throws NoSuchFieldException {
            assertThat(resourceType.getDeclaredField("id").getType()).isEqualTo(UUID.class);
        }

        @ParameterizedTest
        @MethodSource("br.org.gam.api.shared.persistence.UUIDIdentityTest#resourceResponseTypes")
        @DisplayName("COND - resource response id component type -> UUID")
        void resourceResponseIdComponentShouldUseUuidType(Class<?> responseType) {
            RecordComponent idComponent = Arrays.stream(responseType.getRecordComponents())
                    .filter(component -> component.getName().equals("id"))
                    .findFirst()
                    .orElseThrow();

            assertThat(idComponent.getType()).isEqualTo(UUID.class);
        }

        @ParameterizedTest
        @MethodSource("br.org.gam.api.shared.persistence.UUIDIdentityTest#resourceControllerLookupMethods")
        @DisplayName("COND - resource lookup path identifier type -> UUID")
        void resourceLookupPathIdentifierShouldUseUuidType(Class<?> controllerType, String methodName) {
            assertThatCode(() -> controllerType.getDeclaredMethod(methodName, UUID.class))
                    .doesNotThrowAnyException();
        }
    }

    private static Stream<Class<?>> persistedResourceTypes() {
        return Stream.of(
                Account.class,
                AccountEntity.class,
                Event.class,
                EventEntity.class,
                LocationEntity.class,
                Member.class,
                MemberEntity.class,
                Oratoriano.class,
                OratorianoEntity.class
        );
    }

    private static Stream<Class<?>> resourceResponseTypes() {
        return Stream.of(
                AccountRDTO.class,
                EventRDTO.class,
                LocationRDTO.class,
                MemberRDTO.class
        );
    }

    private static Stream<Arguments> resourceControllerLookupMethods() {
        return Stream.of(
                Arguments.of(AccountController.class, "getAccountById"),
                Arguments.of(EventController.class, "getEventById"),
                Arguments.of(LocationController.class, "getLocationById"),
                Arguments.of(MemberController.class, "getMemberById")
        );
    }

    private static Account account() {
        return Account.register(MyEmail.of("person@example.com"), "hash", "Ana Silva");
    }

    private static GamName name() {
        return new GamName("Ana", "Silva");
    }

    private static GamPhoneNumber phoneNumber() {
        return GamPhoneNumber.fromString("+5519998877665");
    }
}
