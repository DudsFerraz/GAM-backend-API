package br.org.gam.api.shared.specification;

import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

public enum ComparationMethods {
    EQUALS {
        @Override
        public <T> Specification<T> create(String field, Object value) {
            return SpecificationFactory.equals(field, value);
        }
    },

    LIKE {
        @Override
        public <T> Specification<T> create(String field, Object value) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Valor para 'LIKE' deve ser uma String.");
            }

            return SpecificationFactory.like(field, (String) value);
        }
    },

    GREATER_THAN_OR_EQUAL {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> Specification<T> create(String field, Object value) {
            if (!(value instanceof Comparable)) {
                throw new IllegalArgumentException("Valor para 'GREATER_THAN_OR_EQUAL' deve ser Comparable (ex: Instant, LocalDate, Integer, Double).");
            }

            return SpecificationFactory.isGreaterThanOrEqual(field, (Comparable) value);
        }
    },

    LESS_THAN_OR_EQUAL {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> Specification<T> create(String field, Object value) {
            if (!(value instanceof Comparable)) {
                throw new IllegalArgumentException("Valor para 'LESS_THAN_OR_EQUAL' deve ser Comparable.");
            }

            return SpecificationFactory.isLessThanOrEqual(field, (Comparable) value);
        }
    },

    IN {
        @Override
        public <T> Specification<T> create(String field, Object value) {
            if (!(value instanceof Collection)) {
                throw new IllegalArgumentException("Valor para 'IN' deve ser uma Collection (ex: List, Set).");
            }

            return SpecificationFactory.in(field, (Collection<?>) value);
        }
    };

    public abstract <T> Specification<T> create(String field, Object value);
}
