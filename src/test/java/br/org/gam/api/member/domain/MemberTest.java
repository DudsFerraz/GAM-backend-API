package br.org.gam.api.member.domain;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@DisplayName("Member Aggregate")
class MemberTest {

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("REQ-MEMBER-001 through REQ-MEMBER-004 - valid registration data -> active Member with generated identity")
        void validRegistrationDataShouldCreateActiveMemberWithGeneratedIdentity() {
            Account account = account();
            GamName name = new GamName("Ana", "Silva");
            LocalDate birthDate = LocalDate.now().minusYears(20);
            GamPhoneNumber phoneNumber = phoneNumber();

            Member member = Member.register(account, name, birthDate, phoneNumber);

            assertThat(member.getId()).isNotNull();
            assertThat(member.getId().version()).isEqualTo(7);
            assertThat(member.getAccount()).isSameAs(account);
            assertThat(member.getName()).isEqualTo(name);
            assertThat(member.getBirthDate()).isEqualTo(birthDate);
            assertThat(member.getPhoneNumber()).isEqualTo(phoneNumber);
            assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        }

        @Test
        @DisplayName("REQ-MEMBER-002 - BVA - seventeenth birthday -> accepted")
        void seventeenthBirthdayShouldBeAccepted() {
            Member member = Member.register(
                    account(),
                    new GamName("Ana", "Silva"),
                    LocalDate.now().minusYears(17),
                    phoneNumber()
            );

            assertThat(member.getAge()).isEqualTo(17);
        }

        @Test
        @DisplayName("REQ-MEMBER-002 - BVA - one day before seventeenth birthday -> validation error")
        void oneDayBeforeSeventeenthBirthdayShouldReturnValidationError() {
            LocalDate underageBirthDate = LocalDate.now().minusYears(17).plusDays(1);

            assertThatThrownBy(() -> Member.register(
                    account(),
                    new GamName("Ana", "Silva"),
                    underageBirthDate,
                    phoneNumber()
            )).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("BVA - future birth date -> validation error")
        void futureBirthDateShouldReturnValidationError() {
            LocalDate tomorrow = LocalDate.now().plusDays(1);

            assertThatThrownBy(() -> Member.register(account(), new GamName("Ana", "Silva"), tomorrow, phoneNumber()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Birth date cannot be in the future.");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null account -> validation error")
        void nullAccountShouldReturnValidationError(Account account) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Member.register(account, new GamName("Ana", "Silva"), LocalDate.now(), phoneNumber()))
                    .withMessage("Account cannot be null.");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null name -> validation error")
        void nullNameShouldReturnValidationError(GamName name) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Member.register(account(), name, LocalDate.now(), phoneNumber()))
                    .withMessage("Name cannot be null.");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null birth date -> validation error")
        void nullBirthDateShouldReturnValidationError(LocalDate birthDate) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Member.register(account(), new GamName("Ana", "Silva"), birthDate, phoneNumber()))
                    .withMessage("Birth date cannot be null.");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null phone number -> validation error")
        void nullPhoneNumberShouldReturnValidationError(GamPhoneNumber phoneNumber) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Member.register(account(), new GamName("Ana", "Silva"), LocalDate.now(), phoneNumber))
                    .withMessage("Phone number cannot be null.");
        }

        @Test
        @DisplayName("REQ-MEMBER-004 - inactive Member activates -> active Member")
        void activateInactiveMemberShouldSetActiveStatus() {
            Member member = Member.register(
                    account(), new GamName("Ana", "Silva"), LocalDate.now().minusYears(20), phoneNumber());
            member.deactivate();

            member.activate();

            assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        }

        @Test
        @DisplayName("REQ-MEMBER-004 - active Member deactivates -> inactive Member")
        void deactivateActiveMemberShouldSetInactiveStatus() {
            Member member = Member.register(
                    account(), new GamName("Ana", "Silva"), LocalDate.now().minusYears(20), phoneNumber());
            member.activate();

            member.deactivate();

            assertThat(member.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        }

        @Test
        @SuppressWarnings("deprecation")
        @DisplayName("REQ-MEMBER-IMPORT-005 - Account-less Member links once -> repeated link rejected")
        void accountLinkShouldBeImmutable() {
            Member member = new Member(
                    java.util.UUID.randomUUID(), null, new GamName("Ana", "Silva"),
                    LocalDate.now().minusYears(20), phoneNumber(), MemberStatus.ACTIVE
            );
            Account first = account();

            member.linkAccount(first);

            assertThat(member.getAccount()).isSameAs(first);
            assertThatThrownBy(() -> member.linkAccount(account()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("REQ-MEMBER-INFO-005 - complete exact experience and sacrament maps -> accepted")
        void completeExactReplacementCatalogsShouldBeAccepted() {
            Member member = Member.register(
                    account(), new GamName("Ana", "Silva"), LocalDate.now().minusYears(20), phoneNumber());
            Map<MemberExperienceType, InformationStatus> completeExperiences =
                    completeStatuses(MemberExperienceType.class, InformationStatus.YES);
            Map<MemberSacramentType, InformationStatus> completeSacraments =
                    completeStatuses(MemberSacramentType.class, InformationStatus.NO);

            member.replaceExperiences(completeExperiences);
            member.replaceSacraments(completeSacraments);

            assertThat(member.getExperiences()).containsExactlyInAnyOrderEntriesOf(completeExperiences);
            assertThat(member.getSacraments()).containsExactlyInAnyOrderEntriesOf(completeSacraments);
        }

        @Test
        @DisplayName("REQ-MEMBER-INFO-005 - non-exact experience catalog -> rejected")
        void nonExactExperienceCatalogShouldBeRejected() {
            Member member = Member.register(
                    account(), new GamName("Ana", "Silva"), LocalDate.now().minusYears(20), phoneNumber());
            Map<MemberExperienceType, InformationStatus> completeExperiences =
                    completeStatuses(MemberExperienceType.class, InformationStatus.YES);

            assertInvalidCatalogs(
                    "experience",
                    member::replaceExperiences,
                    MemberExperienceType.JORNADA_MISSIONARIA,
                    completeExperiences
            );
        }

        @Test
        @DisplayName("REQ-MEMBER-INFO-005 - non-exact sacrament catalog -> rejected")
        void nonExactSacramentCatalogShouldBeRejected() {
            Member member = Member.register(
                    account(), new GamName("Ana", "Silva"), LocalDate.now().minusYears(20), phoneNumber());
            Map<MemberSacramentType, InformationStatus> completeSacraments =
                    completeStatuses(MemberSacramentType.class, InformationStatus.NO);

            assertInvalidCatalogs(
                    "sacrament",
                    member::replaceSacraments,
                    MemberSacramentType.BATISMO,
                    completeSacraments
            );
        }
    }

    private static <E extends Enum<E>> Map<E, InformationStatus> completeStatuses(
            Class<E> type,
            InformationStatus status
    ) {
        EnumMap<E, InformationStatus> values = new EnumMap<>(type);
        for (E key : type.getEnumConstants()) {
            values.put(key, status);
        }
        return Map.copyOf(values);
    }

    private static <E extends Enum<E>> void assertInvalidCatalogs(
            String catalog,
            java.util.function.Consumer<Map<E, InformationStatus>> replacement,
            E firstKey,
            Map<E, InformationStatus> complete
    ) {
        Map<E, InformationStatus> partial = Map.of(firstKey, InformationStatus.YES);
        Map<E, InformationStatus> nullStatus = new LinkedHashMap<>(complete);
        nullStatus.put(firstKey, null);
        Map<E, InformationStatus> extra = extraEntry(complete);

        org.assertj.core.api.SoftAssertions softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThatCode(() -> replacement.accept(Map.of()))
                .as(catalog + " empty catalog")
                .isInstanceOf(IllegalArgumentException.class);
        softly.assertThatCode(() -> replacement.accept(partial))
                .as(catalog + " partial catalog")
                .isInstanceOf(IllegalArgumentException.class);
        softly.assertThatCode(() -> replacement.accept(extra))
                .as(catalog + " extra-key catalog")
                .isInstanceOf(RuntimeException.class);
        softly.assertThatCode(() -> replacement.accept(null))
                .as(catalog + " null catalog")
                .isInstanceOf(IllegalArgumentException.class);
        softly.assertThatCode(() -> replacement.accept(nullStatus))
                .as(catalog + " null-status catalog")
                .isInstanceOf(IllegalArgumentException.class);
        softly.assertAll();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <E extends Enum<E>> Map<E, InformationStatus> extraEntry(Map<E, InformationStatus> complete) {
        Map raw = new LinkedHashMap<>(complete);
        raw.put("SYNTHETIC_EXTRA_CATALOG_KEY", InformationStatus.YES);
        return (Map<E, InformationStatus>) raw;
    }

    private static Account account() {
        return Account.register(GamEmail.of("member@example.com"), "encoded-password", "Member Account");
    }

    private static GamPhoneNumber phoneNumber() {
        return GamPhoneNumber.fromString("+5519998877665");
    }
}
