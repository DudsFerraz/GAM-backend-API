package br.org.gam.api.member.application.useCases.GetMemberInstance;

import br.org.gam.api.account.domain.Account;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberNotFoundException;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.shared.domain.Name;
import br.org.gam.api.shared.phonenumber.MyPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Get Member Instance Use Case")
class SpringGetMemberInstanceTest {

    @Mock
    private MemberRepository memberRepo;

    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private SpringGetMemberInstance getMemberInstance;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - existing id -> domain member")
        void existingIdShouldReturnDomainMember() {
            UUID id = UUID.randomUUID();
            MemberEntity entity = new MemberEntity();
            Member domain = member();

            when(memberRepo.findById(id)).thenReturn(Optional.of(entity));
            when(memberMapper.entityToDomain(entity)).thenReturn(domain);

            Member result = getMemberInstance.domainById(id);

            assertThat(result).isSameAs(domain);
            verify(memberMapper).entityToDomain(entity);
        }

        @Test
        @DisplayName("EP - existing id -> member entity")
        void existingIdShouldReturnMemberEntity() {
            UUID id = UUID.randomUUID();
            MemberEntity entity = new MemberEntity();

            when(memberRepo.findById(id)).thenReturn(Optional.of(entity));

            MemberEntity result = getMemberInstance.entityById(id);

            assertThat(result).isSameAs(entity);
            verifyNoInteractions(memberMapper);
        }

        @Test
        @DisplayName("EP - missing id -> not found error")
        void missingIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(memberRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getMemberInstance.entityById(id))
                    .isInstanceOf(MemberNotFoundException.class)
                    .hasMessage("Could not find member with id " + id);

            verifyNoInteractions(memberMapper);
        }

        @Test
        @DisplayName("EP - existing ids -> domain members")
        void existingIdsShouldReturnDomainMembers() {
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            MemberEntity firstEntity = new MemberEntity();
            MemberEntity secondEntity = new MemberEntity();
            Member firstMember = member();
            Member secondMember = member();

            when(memberRepo.findAllById(Set.of(firstId, secondId))).thenReturn(List.of(firstEntity, secondEntity));
            when(memberMapper.entityToDomain(firstEntity)).thenReturn(firstMember);
            when(memberMapper.entityToDomain(secondEntity)).thenReturn(secondMember);

            Set<Member> members = getMemberInstance.domainsById(Set.of(firstId, secondId));

            assertThat(members).containsExactlyInAnyOrder(firstMember, secondMember);
        }
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("missing id for domain lookup -> not found error")
        void missingIdForDomainLookupShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(memberRepo.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> getMemberInstance.domainById(id))
                    .isInstanceOf(MemberNotFoundException.class)
                    .hasMessage("Could not find member with id " + id);

            verifyNoInteractions(memberMapper);
        }

        @Test
        @DisplayName("null id set -> empty result")
        void nullIdSetShouldReturnEmptyResult() {
            Set<Member> members = getMemberInstance.domainsById(null);

            assertThat(members).isEmpty();
            verifyNoInteractions(memberRepo, memberMapper);
        }

        @Test
        @DisplayName("empty id set -> empty result")
        void emptyIdSetShouldReturnEmptyResult() {
            Set<Member> members = getMemberInstance.domainsById(Set.of());

            assertThat(members).isEmpty();
            verifyNoInteractions(memberRepo, memberMapper);
        }

        @Test
        @DisplayName("id set with null values -> null ids are ignored")
        void idSetWithNullValuesShouldIgnoreNullIds() {
            UUID id = UUID.randomUUID();
            MemberEntity entity = new MemberEntity();
            Member domain = member();

            when(memberRepo.findAllById(Set.of(id))).thenReturn(List.of(entity));
            when(memberMapper.entityToDomain(entity)).thenReturn(domain);

            Set<Member> members = getMemberInstance.domainsById(new java.util.HashSet<>(java.util.Arrays.asList(id, null)));

            assertThat(members).containsExactly(domain);
            verify(memberRepo).findAllById(Set.of(id));
        }
    }

    private static Member member() {
        Account account = Account.register(MyEmail.of(UUID.randomUUID() + "@example.com"), "encoded-password", "Member Account");
        return Member.register(
                account,
                new Name("Ana", "Silva"),
                LocalDate.now().minusYears(20),
                MyPhoneNumber.parse("+5519998877665", "BR")
        );
    }
}
