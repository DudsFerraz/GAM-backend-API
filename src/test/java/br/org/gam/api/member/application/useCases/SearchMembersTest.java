package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.application.MemberMapper;
import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.shared.specification.ComparationMethods;
import br.org.gam.api.shared.specification.SpecificationFilter;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Search Members Use Case")
@SuppressWarnings("unchecked")
class SearchMembersTest {

    @Mock
    private MemberRepository memberRepo;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private SearchMembers searchMembers;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid filters and pageable -> mapped member page")
        void validFiltersAndPageableShouldReturnMappedMemberPage() {
            List<SpecificationFilter> filters = List.of(
                    new SpecificationFilter("status", MemberStatus.ACTIVE, ComparationMethods.EQUALS)
            );
            Pageable pageable = PageRequest.of(0, 10);
            MemberEntity firstEntity = new MemberEntity();
            MemberEntity secondEntity = new MemberEntity();
            MemberRDTO firstResponse = response(UUID.randomUUID(), "Ana Silva");
            MemberRDTO secondResponse = response(UUID.randomUUID(), "Ruy Santos");

            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of());
            when(memberRepo.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(firstEntity, secondEntity), pageable, 2));
            when(memberMapper.entityToRDTO(firstEntity)).thenReturn(firstResponse);
            when(memberMapper.entityToRDTO(secondEntity)).thenReturn(secondResponse);

            Page<MemberRDTO> response = searchMembers.search(filters, pageable);

            assertThat(response.getContent()).containsExactly(firstResponse, secondResponse);
            assertThat(response.getTotalElements()).isEqualTo(2);

            ArgumentCaptor<Specification<MemberEntity>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
            verify(memberRepo).findAll(specificationCaptor.capture(), eq(pageable));
            assertThat(specificationCaptor.getValue()).isNotNull();
            verify(securityUtils).getLoggedUserAuthorities();
            verify(memberMapper).entityToRDTO(firstEntity);
            verify(memberMapper).entityToRDTO(secondEntity);
        }

        @Test
        @DisplayName("EP - empty filters -> mapped member page")
        void emptyFiltersShouldReturnMappedMemberPage() {
            Pageable pageable = PageRequest.of(0, 10);
            MemberEntity entity = new MemberEntity();
            MemberRDTO expectedResponse = response(UUID.randomUUID(), "Ana Silva");

            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of("MEMBER_GET_NON_ACTIVE"));
            when(memberRepo.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));
            when(memberMapper.entityToRDTO(entity)).thenReturn(expectedResponse);

            Page<MemberRDTO> response = searchMembers.search(List.of(), pageable);

            assertThat(response.getContent()).containsExactly(expectedResponse);
            verify(securityUtils).getLoggedUserAuthorities();
            verify(memberRepo).findAll(any(Specification.class), eq(pageable));
            verify(memberMapper).entityToRDTO(entity);
        }

        @Test
        @DisplayName("EP - invalid filter value -> filter is ignored")
        void invalidFilterValueShouldBeIgnored() {
            Pageable pageable = PageRequest.of(0, 10);
            List<SpecificationFilter> filters = List.of(
                    new SpecificationFilter("name.firstName", " ", ComparationMethods.EQUALS)
            );

            when(securityUtils.getLoggedUserAuthorities()).thenReturn(Set.of());
            when(memberRepo.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(Page.empty(pageable));

            Page<MemberRDTO> response = searchMembers.search(filters, pageable);

            assertThat(response.getContent()).isEmpty();
            verify(securityUtils).getLoggedUserAuthorities();
            verify(memberRepo).findAll(any(Specification.class), eq(pageable));
            verifyNoInteractions(memberMapper);
        }
    }

    private static MemberRDTO response(UUID id, String name) {
        return new MemberRDTO(id, null, name, LocalDate.now().minusYears(20), null, MemberStatus.ACTIVE);
    }
}
