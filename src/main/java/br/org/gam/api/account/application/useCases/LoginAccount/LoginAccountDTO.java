package br.org.gam.api.account.application.useCases.LoginAccount;

import br.org.gam.api.account.domain.MyEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

public record LoginAccountDTO(
        @NotNull @Valid MyEmail email,
        @NotNull @NotBlank String password) {
}
