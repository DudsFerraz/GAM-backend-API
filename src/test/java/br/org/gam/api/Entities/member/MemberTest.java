package br.org.gam.api.Entities.member;

import br.org.gam.api.Entities.account.Account;
import br.org.gam.api.Entities.account.myEmail.MyEmail;
import br.org.gam.api.common.Name;
import br.org.gam.api.common.myPhoneNumber.MyPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;

import java.time.LocalDate;

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
        @DisplayName("EP - valid registration data -> pendent member with generated identity")
        void validRegistrationDataShouldCreatePendentMemberWithGeneratedIdentity() {
            Account account = account();
            Name name = new Name("Ana", "Silva");
            LocalDate birthDate = LocalDate.now().minusYears(20);
            MyPhoneNumber phoneNumber = phoneNumber();

            Member member = Member.register(account, name, birthDate, phoneNumber);

            assertThat(member.getId()).isNotNull();
            assertThat(member.getId().version()).isEqualTo(7);
            assertThat(member.getAccount()).isSameAs(account);
            assertThat(member.getName()).isEqualTo(name);
            assertThat(member.getBirthDate()).isEqualTo(birthDate);
            assertThat(member.getPhoneNumber()).isEqualTo(phoneNumber);
            assertThat(member.getStatus()).isEqualTo(MemberStatus.PENDENT);
        }

        @Test
        @DisplayName("BVA - birth date today -> accepted")
        void birthDateTodayShouldBeAccepted() {
            Member member = Member.register(account(), new Name("Ana", "Silva"), LocalDate.now(), phoneNumber());

            assertThat(member.getAge()).isZero();
        }

        @Test
        @DisplayName("BVA - future birth date -> validation error")
        void futureBirthDateShouldReturnValidationError() {
            LocalDate tomorrow = LocalDate.now().plusDays(1);

            assertThatThrownBy(() -> Member.register(account(), new Name("Ana", "Silva"), tomorrow, phoneNumber()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Birth date cannot be in the future.");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null account -> validation error")
        void nullAccountShouldReturnValidationError(Account account) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Member.register(account, new Name("Ana", "Silva"), LocalDate.now(), phoneNumber()))
                    .withMessage("Account cannot be null.");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null name -> validation error")
        void nullNameShouldReturnValidationError(Name name) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Member.register(account(), name, LocalDate.now(), phoneNumber()))
                    .withMessage("Name cannot be null.");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null birth date -> validation error")
        void nullBirthDateShouldReturnValidationError(LocalDate birthDate) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Member.register(account(), new Name("Ana", "Silva"), birthDate, phoneNumber()))
                    .withMessage("Birth date cannot be null.");
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("EP - null phone number -> validation error")
        void nullPhoneNumberShouldReturnValidationError(MyPhoneNumber phoneNumber) {
            assertThatNullPointerException()
                    .isThrownBy(() -> Member.register(account(), new Name("Ana", "Silva"), LocalDate.now(), phoneNumber))
                    .withMessage("Phone number cannot be null.");
        }

        @Test
        @DisplayName("EP - activate pendent member -> active member")
        void activatePendentMemberShouldSetActiveStatus() {
            Member member = Member.register(account(), new Name("Ana", "Silva"), LocalDate.now(), phoneNumber());

            member.activate();

            assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        }

        @Test
        @DisplayName("EP - deactivate member -> inactive member")
        void deactivateMemberShouldSetInactiveStatus() {
            Member member = Member.register(account(), new Name("Ana", "Silva"), LocalDate.now(), phoneNumber());

            member.deactivate();

            assertThat(member.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        }
    }

    private static Account account() {
        return Account.register(MyEmail.of("member@example.com"), "encoded-password", "Member Account");
    }

    private static MyPhoneNumber phoneNumber() {
        return MyPhoneNumber.parse("+5519998877665", "BR");
    }
}
