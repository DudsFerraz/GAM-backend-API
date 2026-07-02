package br.org.gam.api.account.application.useCases;

import br.org.gam.api.account.application.AccountMapper;
import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.account.application.search.AccountSearchFilterConverter;
import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.account.persistence.AccountEntity;
import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.rbac.AccountRole.application.AccountRolesRDTO;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.List;
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
@DisplayName("Search Accounts Use Case")
@SuppressWarnings("unchecked")
class SearchAccountsTest {

    @Mock
    private AccountRepository accountRepo;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountSearchFilterConverter searchFilterConverter;

    @InjectMocks
    private SearchAccounts searchAccounts;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid filters and pageable -> mapped account page")
        void validFiltersAndPageableShouldReturnMappedAccountPage() {
            SearchDTO searchDTO = new SearchDTO(List.of());
            Specification<AccountEntity> searchSpecification = Specification.allOf(List.of());
            Pageable pageable = PageRequest.of(0, 10);
            AccountEntity firstEntity = new AccountEntity();
            AccountEntity secondEntity = new AccountEntity();
            AccountRDTO firstResponse = response(UUID.randomUUID(), "first@example.com", "First");
            AccountRDTO secondResponse = response(UUID.randomUUID(), "second@example.com", "Second");

            when(searchFilterConverter.convert(searchDTO)).thenReturn(searchSpecification);
            when(accountRepo.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(firstEntity, secondEntity), pageable, 2));
            when(accountMapper.entityToRDTO(firstEntity)).thenReturn(firstResponse);
            when(accountMapper.entityToRDTO(secondEntity)).thenReturn(secondResponse);

            Page<AccountRDTO> response = searchAccounts.search(searchDTO, pageable);

            assertThat(response.getContent()).containsExactly(firstResponse, secondResponse);
            assertThat(response.getTotalElements()).isEqualTo(2);

            ArgumentCaptor<Specification<AccountEntity>> specificationCaptor = ArgumentCaptor.forClass(Specification.class);
            verify(accountRepo).findAll(specificationCaptor.capture(), eq(pageable));
            assertThat(specificationCaptor.getValue()).isNotNull();
            verify(searchFilterConverter).convert(searchDTO);
            verify(accountMapper).entityToRDTO(firstEntity);
            verify(accountMapper).entityToRDTO(secondEntity);
        }

        @Test
        @DisplayName("EP - empty filters -> mapped account page")
        void emptyFiltersShouldReturnMappedAccountPage() {
            SearchDTO searchDTO = new SearchDTO(List.of());
            Specification<AccountEntity> searchSpecification = Specification.allOf(List.of());
            Pageable pageable = PageRequest.of(0, 10);
            AccountEntity entity = new AccountEntity();
            AccountRDTO expectedResponse = response(UUID.randomUUID(), "user@example.com", "User");

            when(searchFilterConverter.convert(searchDTO)).thenReturn(searchSpecification);
            when(accountRepo.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));
            when(accountMapper.entityToRDTO(entity)).thenReturn(expectedResponse);

            Page<AccountRDTO> response = searchAccounts.search(searchDTO, pageable);

            assertThat(response.getContent()).containsExactly(expectedResponse);
            verify(searchFilterConverter).convert(searchDTO);
            verify(accountRepo).findAll(any(Specification.class), eq(pageable));
            verify(accountMapper).entityToRDTO(entity);
        }

        @Test
        @DisplayName("EP - no matching records -> empty page")
        void noMatchingRecordsShouldReturnEmptyPage() {
            SearchDTO searchDTO = new SearchDTO(List.of());
            Specification<AccountEntity> searchSpecification = Specification.allOf(List.of());
            Pageable pageable = PageRequest.of(0, 10);

            when(searchFilterConverter.convert(searchDTO)).thenReturn(searchSpecification);
            when(accountRepo.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(Page.empty(pageable));

            Page<AccountRDTO> response = searchAccounts.search(searchDTO, pageable);

            assertThat(response.getContent()).isEmpty();
            verify(searchFilterConverter).convert(searchDTO);
            verify(accountRepo).findAll(any(Specification.class), eq(pageable));
            verifyNoInteractions(accountMapper);
        }
    }

    private static AccountRDTO response(UUID id, String email, String displayName) {
        return new AccountRDTO(id, MyEmail.of(email), displayName, new AccountRolesRDTO());
    }
}
