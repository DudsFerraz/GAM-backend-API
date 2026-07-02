package br.org.gam.api.location.application.useCases.CreateLocation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.lang.Nullable;

public record CreateLocationDTO(
        @NotNull @NotBlank String name,
        @Nullable String street,
        @NotNull @NotBlank String city,
        @NotNull @NotBlank String state,
        @Nullable String postalCode,
        @NotNull @NotBlank String countryCode,
        @Nullable BigDecimal latitude,
        @Nullable BigDecimal longitude
) {
}
