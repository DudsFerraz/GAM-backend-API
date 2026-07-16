package br.org.gam.api.account.web;

import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.account.application.useCases.GetAccount;
import br.org.gam.api.account.application.useCases.SearchAccounts;
import br.org.gam.api.account.application.useCases.getCurrentAccountContext.CurrentAccountContextRDTO;
import br.org.gam.api.account.application.useCases.getCurrentAccountContext.GetCurrentAccountContext;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.security.application.AccountDetails;
import br.org.gam.api.shared.specification.SearchDTO;
import br.org.gam.api.shared.web.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final GetAccount getAccount;
    private final GetCurrentAccountContext getCurrentAccountContext;
    private final SearchAccounts searchAccountsService;

    public AccountController(
            GetAccount getAccount,
            GetCurrentAccountContext getCurrentAccountContext,
            SearchAccounts searchAccountsService
    ) {
        this.getAccount = getAccount;
        this.getCurrentAccountContext = getCurrentAccountContext;
        this.searchAccountsService = searchAccountsService;
    }

    @Operation(
            operationId = "getCurrentAccountContext",
            summary = "Get current Account context",
            description = "Returns the authenticated Account identity, active roles, and distinct currently effective "
                    + "permission codes for frontend capability synchronization."
    )
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CurrentAccountContextRDTO> getCurrentAccountContext(
            @AuthenticationPrincipal AccountDetails accountDetails
    ) {
        return ResponseEntity.ok(getCurrentAccountContext.get(accountDetails.getId()));
    }

    @PreAuthorize("@accountSecurity.canGetAccount(#id)")
    @Operation(operationId = "getAccount")
    @GetMapping("/{id}")
    public ResponseEntity<AccountRDTO> getAccountById(@PathVariable UUID id) {
        AccountRDTO dto = getAccount.byId(id);
        return ResponseEntity.ok(dto);
    }

    @PreAuthorize("hasAuthority('" + PermissionEnum.Code.ACCOUNT_SEARCH + "')")
    @Operation(operationId = "searchAccounts")
    @PostMapping("/search")
    public ResponseEntity<PagedResponse<AccountRDTO>> searchAccounts(@RequestBody @Valid SearchDTO searchDTO,
                                                                       Pageable pageable) {

        return ResponseEntity.ok(PagedResponse.from(searchAccountsService.search(searchDTO, pageable)));
    }
}
