package br.org.gam.api.member.application.useCases;

import jakarta.validation.constraints.NotBlank;

public record DeactivateMemberDTO(
        @NotBlank(message = "Deactivation reason is required.")
        String reason
) {
}
