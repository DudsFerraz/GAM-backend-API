package br.org.gam.api.Entities.member.services;

import br.org.gam.api.Entities.account.Account;
import br.org.gam.api.Entities.account.myEmail.MyEmail;
import br.org.gam.api.Entities.account.services.getAccountInstance.GetAccountInstance;
import br.org.gam.api.Entities.member.Member;
import br.org.gam.api.Entities.member.MemberMapper;
import br.org.gam.api.Entities.member.MemberStatus;
import br.org.gam.api.Entities.member.exception.MemberAccountConflictException;
import br.org.gam.api.Entities.member.persistence.MemberEntity;
import br.org.gam.api.Entities.member.persistence.MemberRepository;
import br.org.gam.api.Entities.member.services.registerMember.RegisterMemberDTO;
import br.org.gam.api.Entities.member.services.registerMember.RegisterMemberRDTO;
import br.org.gam.api.Entities.member.services.registerMember.SpringRegisterMember;
import br.org.gam.api.common.myPhoneNumber.MyPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

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
class SpringRegisterMemberTest {

    @Mock
    private MemberRepository memberRepo;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private GetAccountInstance getAccountInstance;

    @InjectMocks
    private SpringRegisterMember registerMember;

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
            when(getAccountInstance.domainById(accountId)).thenReturn(account);
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
