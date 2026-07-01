package br.org.gam.api.common.specification;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@DisplayName("Specification Factory")
@SuppressWarnings({"unchecked", "rawtypes"})
class SpecificationFactoryStructuralTest {

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("equals on direct field -> distinct equality predicate")
        void equalsOnDirectFieldShouldReturnDistinctEqualityPredicate() {
            Root<Object> root = mock(Root.class);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder builder = mock(CriteriaBuilder.class);
            Path<Object> path = mock(Path.class);
            Predicate predicate = mock(Predicate.class);

            when(root.get("name")).thenReturn(path);
            when(builder.equal(path, "Ana")).thenReturn(predicate);

            Predicate result = SpecificationFactory.equals("name", "Ana").toPredicate(root, query, builder);

            assertThat(result).isSameAs(predicate);
            verify(query).distinct(true);
            verify(builder).equal(path, "Ana");
        }

        @Test
        @DisplayName("equals on nested field -> left join path")
        void equalsOnNestedFieldShouldUseLeftJoinPath() {
            Root<Object> root = mock(Root.class);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder builder = mock(CriteriaBuilder.class);
            Join<Object, Object> accountJoin = mock(Join.class);
            Path<Object> path = mock(Path.class);
            Predicate predicate = mock(Predicate.class);

            when(root.join("account", JoinType.LEFT)).thenReturn(accountJoin);
            when(accountJoin.get("email")).thenReturn(path);
            when(builder.equal(path, "user@example.com")).thenReturn(predicate);

            Predicate result = SpecificationFactory.equals("account.email", "user@example.com").toPredicate(root, query, builder);

            assertThat(result).isSameAs(predicate);
            verify(root).join("account", JoinType.LEFT);
            verify(accountJoin).get("email");
        }

        @Test
        @DisplayName("like value -> lower case contains predicate")
        void likeValueShouldReturnLowerCaseContainsPredicate() {
            Root<Object> root = mock(Root.class);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder builder = mock(CriteriaBuilder.class);
            Path<Object> path = mock(Path.class);
            Expression<String> stringExpression = mock(Expression.class);
            Expression<String> lowerExpression = mock(Expression.class);
            Predicate predicate = mock(Predicate.class);

            when(root.get("name")).thenReturn(path);
            when(path.as(String.class)).thenReturn(stringExpression);
            when(builder.lower(stringExpression)).thenReturn(lowerExpression);
            when(builder.like(lowerExpression, "%ana%")).thenReturn(predicate);

            Predicate result = SpecificationFactory.like("name", "Ana").toPredicate(root, query, builder);

            assertThat(result).isSameAs(predicate);
            verify(builder).like(lowerExpression, "%ana%");
        }

        @Test
        @DisplayName("in value collection -> path in predicate")
        void inValueCollectionShouldReturnPathInPredicate() {
            Root<Object> root = mock(Root.class);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder builder = mock(CriteriaBuilder.class);
            Path<Object> path = mock(Path.class);
            Predicate predicate = mock(Predicate.class);
            List<String> values = List.of("ACTIVE", "PENDENT");

            when(root.get("status")).thenReturn(path);
            when(path.in(values)).thenReturn(predicate);

            Predicate result = SpecificationFactory.in("status", values).toPredicate(root, query, builder);

            assertThat(result).isSameAs(predicate);
            verify(query).distinct(true);
            verify(path).in(values);
        }
    }
}
