package br.org.gam.api.account.application.useCases.registerAccount;

import br.org.gam.api.shared.domain.GamEmail;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

public record RegisterAccountDTO(
        @NotNull @Valid GamEmail email,

        @NotNull @NotBlank @Size(min = 8, max = 128) String password,

        @NotNull @NotBlank String displayName) {
}
