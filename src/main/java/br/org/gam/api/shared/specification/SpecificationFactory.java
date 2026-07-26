package br.org.gam.api.shared.specification;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
            Expression<String> expression = cb.lower(getPath(root, field).as(String.class));
            String pattern = "%" + escapeLikeLiteral(value.toLowerCase(Locale.ROOT)) + "%";
            return containsLikeMeta(value)
                    ? cb.like(expression, pattern, '\\')
                    : cb.like(expression, pattern);
        };
    }

    public static <T> Specification<T> likeAny(List<String> fields, String value) {
        return (root, query, cb) -> {
            query.distinct(true);
            String pattern = "%" + escapeLikeLiteral(value.toLowerCase(Locale.ROOT)) + "%";
            return cb.or(fields.stream()
                    .map(field -> {
                        Expression<String> expression = cb.lower(getPath(root, field).as(String.class));
                        return containsLikeMeta(value)
                                ? cb.like(expression, pattern, '\\')
                                : cb.like(expression, pattern);
                    })
                    .toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    public static <T> Specification<T> likeFullName(
            String firstNameField,
            String surnameField,
            String value
    ) {
        return (root, query, cb) -> {
            query.distinct(true);
            Expression<String> fullName = cb.concat(
                    cb.concat(getPath(root, firstNameField).as(String.class), " "),
                    getPath(root, surnameField).as(String.class)
            );
            Expression<String> expression = cb.lower(fullName);
            String pattern = "%" + escapeLikeLiteral(value.toLowerCase(Locale.ROOT)) + "%";
            return containsLikeMeta(value)
                    ? cb.like(expression, pattern, '\\')
                    : cb.like(expression, pattern);
        };
    }

    private static String escapeLikeLiteral(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean containsLikeMeta(String value) {
        return value.indexOf('%') >= 0 || value.indexOf('_') >= 0 || value.indexOf('\\') >= 0;
    }

    public static <T, C extends Comparable<? super C>> Specification<T> isGreaterThanOrEqual(String field, C value) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.greaterThanOrEqualTo(comparablePath(root, field, value), value);
        };
    }

    public static <T, C extends Comparable<? super C>> Specification<T> isLessThanOrEqual(String field, C value) {
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.lessThanOrEqualTo(comparablePath(root, field, value), value);
        };
    }

    private static <C extends Comparable<? super C>> Expression<C> comparablePath(Root<?> root, String field, C value) {
        Path<Object> path = getPath(root, field);
        if (!path.getJavaType().isInstance(value)) {
            throw new IllegalArgumentException("Comparison value type does not match the target field type.");
        }

        Expression<?> expression = path;
        @SuppressWarnings("unchecked")
        Expression<C> typedPath = (Expression<C>) expression;
        return typedPath;
    }

    public static <T> Specification<T> in(String field, Collection<?> values) {
        return (root, query, cb) -> {
            query.distinct(true);
            return getPath(root, field).in(values);
        };
    }
}
