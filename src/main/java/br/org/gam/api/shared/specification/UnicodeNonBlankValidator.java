package br.org.gam.api.shared.specification;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

final class UnicodeNonBlankValidator implements ConstraintValidator<UnicodeNonBlank, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && !UnicodeWhitespace.isBlank(value);
    }
}
