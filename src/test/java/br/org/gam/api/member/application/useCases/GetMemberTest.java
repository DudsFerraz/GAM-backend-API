package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.application.MemberSecurity;
import br.org.gam.api.member.application.MemberEntityLoader;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.LocalDate;
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
@DisplayName("Get Member Use Case")
class GetMemberTest {

    @Mock
    private MemberEntityLoader getMemberInstance;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private MemberSecurity memberSecurity;

    @InjectMocks
    private GetMember getMember;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - visible member id -> member response")
        void visibleMemberIdShouldReturnMemberResponse() {
            UUID id = UUID.randomUUID();
            MemberEntity entity = new MemberEntity();
            MemberRDTO expectedResponse = response(id);

            when(getMemberInstance.requiredById(id)).thenReturn(entity);
            when(memberSecurity.canGetMember(entity)).thenReturn(true);
            when(memberMapper.entityToRDTO(entity)).thenReturn(expectedResponse);

            MemberRDTO response = getMember.byId(id);

            assertThat(response).isSameAs(expectedResponse);
            verify(getMemberInstance).requiredById(id);
            verify(memberSecurity).canGetMember(entity);
            verify(memberMapper).entityToRDTO(entity);
        }

        @Test
        @DisplayName("EP - hidden member id -> not found error")
        void hiddenMemberIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();
            MemberEntity entity = new MemberEntity();

            when(getMemberInstance.requiredById(id)).thenReturn(entity);
            when(memberSecurity.canGetMember(entity)).thenReturn(false);

            assertThatThrownBy(() -> getMember.byId(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Member not found with identifier " + id);

            verify(memberSecurity).canGetMember(entity);
            verifyNoInteractions(memberMapper);
        }

        @Test
        @DisplayName("EP - missing member id -> not found error")
        void missingMemberIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(getMemberInstance.requiredById(id))
                    .thenThrow(NotFoundException.resource("Member", id));

            assertThatThrownBy(() -> getMember.byId(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Member not found with identifier " + id);

            verifyNoInteractions(memberSecurity, memberMapper);
        }
    }

    private static MemberRDTO response(UUID id) {
        return new MemberRDTO(id, null, "Ana Silva", LocalDate.now().minusYears(20), null, MemberStatus.ACTIVE);
    }
}
