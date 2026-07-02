package br.org.gam.api.shared.specification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SpecificationFilterDTO(
        @NotNull @NotBlank String field,
        @NotNull @NotBlank String value,
        @NotNull ComparationMethods comparationMethod
) {
}
