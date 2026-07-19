package br.org.gam.api.gamLocation.application.useCases;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RemoveGamLocationDTO(
        @NotNull @NotBlank @Size(min = 1, max = 2_000) String reason
) {
}
