package br.org.gam.api.shared.specification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SpecificationFilterDTO(
        @NotNull @NotBlank String field,
        @NotNull JsonNode value,
        @NotNull ComparationMethods comparationMethod
) {
    public SpecificationFilterDTO(String field, String value, ComparationMethods comparationMethod) {
        this(field, TextNode.valueOf(value), comparationMethod);
    }
}
