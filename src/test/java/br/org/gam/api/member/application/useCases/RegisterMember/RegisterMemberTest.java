package br.org.gam.api.member.application.useCases.RegisterMember;

import br.org.gam.api.account.application.AccountDomainLoader;
import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.member.application.MemberAccountConflictException;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.shared.phonenumber.MyPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.LocalDate;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Register Member Use Case")
class RegisterMemberTest {

    @Mock
    private MemberRepository memberRepo;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private AccountDomainLoader getAccountInstance;

    @InjectMocks
    private RegisterMember registerMember;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - account without member -> member is registered")
        void accountWithoutMemberShouldRegisterMember() {
            UUID accountId = UUID.randomUUID();
            Account account = Account.register(MyEmail.of("member@example.com"), "encoded-password", "Member Account");
            MyPhoneNumber phoneNumber = phoneNumber();
            RegisterMemberDTO dto = new RegisterMemberDTO(accountId, "Ana", "Silva", LocalDate.now().minusYears(20), phoneNumber);
            MemberEntity mappedEntity = new MemberEntity();
            MemberEntity savedEntity = new MemberEntity();
            RegisterMemberRDTO expectedResponse = new RegisterMemberRDTO(UUID.randomUUID());

            when(memberRepo.existsByAccountId(accountId)).thenReturn(false);
            when(getAccountInstance.requiredById(accountId)).thenReturn(account);
            when(memberMapper.domainToEntity(any(Member.class))).thenReturn(mappedEntity);
            when(memberRepo.save(mappedEntity)).thenReturn(savedEntity);
            when(memberMapper.entityToRegisterMemberRDTO(savedEntity)).thenReturn(expectedResponse);

            RegisterMemberRDTO response = registerMember.register(dto);

            assertThat(response).isSameAs(expectedResponse);

            ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
            verify(memberMapper).domainToEntity(memberCaptor.capture());
            Member registeredMember = memberCaptor.getValue();

            assertThat(registeredMember.getId()).isNotNull();
            assertThat(registeredMember.getId().version()).isEqualTo(7);
            assertThat(registeredMember.getAccount()).isSameAs(account);
            assertThat(registeredMember.getName().firstName()).isEqualTo("Ana");
            assertThat(registeredMember.getName().surname()).isEqualTo("Silva");
            assertThat(registeredMember.getBirthDate()).isEqualTo(dto.birthDate());
            assertThat(registeredMember.getPhoneNumber()).isSameAs(phoneNumber);
            assertThat(registeredMember.getStatus()).isEqualTo(MemberStatus.PENDENT);
            verify(memberRepo).save(mappedEntity);
        }

        @Test
        @DisplayName("EP - account already linked to member -> conflict error")
        void accountAlreadyLinkedToMemberShouldReturnConflictError() {
            UUID accountId = UUID.randomUUID();
            RegisterMemberDTO dto = new RegisterMemberDTO(accountId, "Ana", "Silva", LocalDate.now(), phoneNumber());

            when(memberRepo.existsByAccountId(accountId)).thenReturn(true);

            assertThatThrownBy(() -> registerMember.register(dto))
                    .isInstanceOf(MemberAccountConflictException.class)
                    .hasMessage("A member is already linked to this account.");

            verifyNoInteractions(getAccountInstance, memberMapper);
            verify(memberRepo, never()).save(any());
        }
    }

    private static MyPhoneNumber phoneNumber() {
        return MyPhoneNumber.parse("+5519998877665", "BR");
    }
}
