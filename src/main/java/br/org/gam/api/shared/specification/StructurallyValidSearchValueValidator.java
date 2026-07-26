package br.org.gam.api.shared.specification;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

final class StructurallyValidSearchValueValidator
        implements ConstraintValidator<StructurallyValidSearchValue, JsonNode> {

    @Override
    public boolean isValid(JsonNode value, ConstraintValidatorContext context) {
        return value == null
                || (!value.isNull() && (!value.isTextual() || !UnicodeWhitespace.isBlank(value.textValue())));
    }
}
