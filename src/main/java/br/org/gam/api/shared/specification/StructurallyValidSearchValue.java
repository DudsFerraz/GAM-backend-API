package br.org.gam.api.shared.specification;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = StructurallyValidSearchValueValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StructurallyValidSearchValue {

    String message() default "must not be a blank textual value";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
