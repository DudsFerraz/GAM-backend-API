package br.org.gam.api.shared.specification;

public record SpecificationFilter(
        String field,
        Object value,
        ComparationMethods comparationMethod
) {

    public boolean isValid() {
        if (value == null) return false;
        if (value instanceof String s) return !s.isBlank();

        return true;
    }

}
