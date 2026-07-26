package br.org.gam.api.shared.specification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SearchDTO(
        @NotNull
        @Size(max = 20)
        @Valid
        @ArraySchema(
                minItems = 0,
                maxItems = 20,
                schema = @Schema(implementation = SpecificationFilterDTO.class)
        )
        List<@NotNull @Valid SpecificationFilterDTO> filters
) {
}
