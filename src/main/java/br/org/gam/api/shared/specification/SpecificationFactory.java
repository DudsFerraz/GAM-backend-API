package br.org.gam.api.shared.specification;

import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class SpecificationFactory {
    private SpecificationFactory() {}

    private static Path<Object> getPath(Root<?> root, String field) {
        String[] parts = field.split("\\.");
        From<?, ?> from = root;
        Path<Object> path = null;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            if (i < parts.length - 1) {
                from = from.join(part, JoinType.LEFT);
            } else {
                path = from.get(part);
            }
        }
        return path;
    }

    public static <T> Specification<T> equals(String field, Object value) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.equal(getPath(root, field), value);
        };
    }

    public static <T> Specification<T> like(String field, String value) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.like(cb.lower(getPath(root, field).as(String.class)), "%" + value.toLowerCase() + "%");
        };
    }

    public static <T> Specification<T> likeAny(List<String> fields, String value) {
        return (root, query, cb) -> {
            query.distinct(true);
            String pattern = "%" + value.toLowerCase() + "%";
            return cb.or(fields.stream()
                    .map(field -> cb.like(cb.lower(getPath(root, field).as(String.class)), pattern))
                    .toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    public static <T, C extends Comparable<? super C>> Specification<T> isGreaterThanOrEqual(String field, C value) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.greaterThanOrEqualTo(getPath(root, field).as((Class<C>) value.getClass()), value);
        };
    }

    public static <T, C extends Comparable<? super C>> Specification<T> isLessThanOrEqual(String field, C value) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.lessThanOrEqualTo(getPath(root, field).as((Class<C>) value.getClass()), value);
        };
    }

    public static <T> Specification<T> in(String field, Collection<?> values) {
        return (root, query, cb) -> {
            query.distinct(true);
            return getPath(root, field).in(values);
        };
    }
}
