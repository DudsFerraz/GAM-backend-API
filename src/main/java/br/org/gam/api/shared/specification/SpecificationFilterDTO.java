package br.org.gam.api.shared.specification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.TextNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SpecificationFilterDTO(
        @NotBlank
        @UnicodeNonBlank
        @JsonDeserialize(using = StrictStringDeserializer.class)
        String field,
        @NotNull @StructurallyValidSearchValue JsonNode value,
        @NotBlank
        @UnicodeNonBlank
        @JsonDeserialize(using = StrictStringDeserializer.class)
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {
                        "EQUALS",
                        "LIKE",
                        "IN",
                        "GREATER_THAN_OR_EQUAL",
                        "LESS_THAN_OR_EQUAL"
                }
        )
        String comparisonMethod
) {
    public SpecificationFilterDTO(String field, String value, ComparationMethods comparationMethod) {
        this(field, TextNode.valueOf(value), comparationMethod.name());
    }
}
