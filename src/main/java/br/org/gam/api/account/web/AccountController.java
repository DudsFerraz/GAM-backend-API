package br.org.gam.api.account.web;

import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.account.application.useCases.GetAccount.GetAccount;
import br.org.gam.api.account.application.useCases.SearchAccounts.SearchAccounts;
import br.org.gam.api.rbac.Permission.domain.PermissionEnum;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.specification.SpecificationFilter;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account")
public class AccountController {

    private final GetAccount getAccount;
    private final SearchAccounts searchAccountsService;
    private final SpecificationFilterConverter specificationFilterConverter;

    public AccountController(GetAccount getAccount,
                             SearchAccounts searchAccountsService,
                             @Qualifier("accountSpecificationFilterConverter") SpecificationFilterConverter specificationFilterConverter) {

        this.getAccount = getAccount;
        this.searchAccountsService = searchAccountsService;
        this.specificationFilterConverter = specificationFilterConverter;
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ACCOUNT_GET + "')")
    @GetMapping("/{id}")
    public ResponseEntity<AccountRDTO> getAccountById(@PathVariable UUID id) {
        AccountRDTO dto = getAccount.byId(id);
        return ResponseEntity.ok(dto);
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ACCOUNT_SEARCH + "')")
    @PostMapping("/search")
    public ResponseEntity<Page<AccountRDTO>> searchAccounts(@RequestBody @Valid SearchDTO searchDTO,
                                                            Pageable pageable) {

        List<SpecificationFilter> filters = specificationFilterConverter.convert(searchDTO.filters());

        return ResponseEntity.ok(
                searchAccountsService.search(filters, pageable)
        );
    }
}
