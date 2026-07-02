package br.org.gam.api.member.application.useCases.GetMember;

import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberNotFoundException;
import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.application.MemberSecurity;
import br.org.gam.api.member.application.useCases.GetMemberInstance.GetMemberInstance;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
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
class SpringGetMemberTest {

    @Mock
    private GetMemberInstance getMemberInstance;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private MemberSecurity memberSecurity;

    @InjectMocks
    private SpringGetMember getMember;

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

            when(getMemberInstance.entityById(id)).thenReturn(entity);
            when(memberSecurity.canGetMember(entity)).thenReturn(true);
            when(memberMapper.entityToMemberRDTO(entity)).thenReturn(expectedResponse);

            MemberRDTO response = getMember.byId(id);

            assertThat(response).isSameAs(expectedResponse);
            verify(getMemberInstance).entityById(id);
            verify(memberSecurity).canGetMember(entity);
            verify(memberMapper).entityToMemberRDTO(entity);
        }

        @Test
        @DisplayName("EP - hidden member id -> not found error")
        void hiddenMemberIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();
            MemberEntity entity = new MemberEntity();

            when(getMemberInstance.entityById(id)).thenReturn(entity);
            when(memberSecurity.canGetMember(entity)).thenReturn(false);

            assertThatThrownBy(() -> getMember.byId(id))
                    .isInstanceOf(MemberNotFoundException.class)
                    .hasMessage("Could not find member with id " + id);

            verify(memberSecurity).canGetMember(entity);
            verifyNoInteractions(memberMapper);
        }

        @Test
        @DisplayName("EP - missing member id -> not found error")
        void missingMemberIdShouldReturnNotFoundError() {
            UUID id = UUID.randomUUID();

            when(getMemberInstance.entityById(id))
                    .thenThrow(new MemberNotFoundException("Could not find member with id " + id));

            assertThatThrownBy(() -> getMember.byId(id))
                    .isInstanceOf(MemberNotFoundException.class)
                    .hasMessage("Could not find member with id " + id);

            verifyNoInteractions(memberSecurity, memberMapper);
        }
    }

    private static MemberRDTO response(UUID id) {
        return new MemberRDTO(id, null, "Ana Silva", LocalDate.now().minusYears(20), null, MemberStatus.ACTIVE);
    }
}
