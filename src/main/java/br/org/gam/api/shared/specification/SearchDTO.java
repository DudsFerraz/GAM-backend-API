package br.org.gam.api.shared.specification;

import jakarta.validation.Valid;
import java.util.List;

public record SearchDTO(
        @Valid List<SpecificationFilterDTO> filters
) {
}
