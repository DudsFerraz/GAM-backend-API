package br.org.gam.api.account.application.useCases.loginAccount;

import br.org.gam.api.shared.domain.GamEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

public record LoginAccountDTO(
        @NotNull @Valid GamEmail email,
        @NotNull @NotBlank String password) {
}
