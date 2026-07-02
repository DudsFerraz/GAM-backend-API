package br.org.gam.api.shared.specification;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@FunctionalTest
@DisplayName("Functional - Specification Filters")
class SpecificationFilterFunctionalTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t"})
    @DisplayName("EP - invalid filter value -> filter is ignored")
    void invalidFilterValueShouldBeIgnored(String value) {
        SpecificationFilter filter = new SpecificationFilter("name", value, ComparationMethods.EQUALS);

        assertThat(filter.isValid()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria", "0"})
    @DisplayName("EP - non-blank string value -> filter is valid")
    void nonBlankStringValueShouldBeValid(String value) {
        SpecificationFilter filter = new SpecificationFilter("name", value, ComparationMethods.EQUALS);

        assertThat(filter.isValid()).isTrue();
    }

    @Test
    @DisplayName("EP - non-string value -> filter is valid")
    void nonStringValueShouldBeValid() {
        SpecificationFilter filter = new SpecificationFilter("birthDate", Instant.EPOCH, ComparationMethods.GREATER_THAN_OR_EQUAL);

        assertThat(filter.isValid()).isTrue();
    }

    @Test
    @DisplayName("EP - null DTO list -> empty specification")
    void nullDtoListShouldReturnEmptySpecification() {
        org.springframework.data.jpa.domain.Specification<Object> specification =
                ResourceSearchFilterConverter.convert(new SearchDTO(null), Map.of());

        assertThat(specification).isNotNull();
    }

    @Test
    @DisplayName("EP - unknown filter field -> validation error")
    void unknownFilterFieldShouldReturnValidationError() {
        SpecificationFilterDTO dto = new SpecificationFilterDTO("unknown", "value", ComparationMethods.EQUALS);

        assertThatThrownBy(() -> ResourceSearchFilterConverter.convert(new SearchDTO(List.of(dto)), Map.of()))
                .isInstanceOf(InvalidSearchFilterException.class)
                .hasMessage("Unknown filter field unknown.");
    }

    @Test
    @DisplayName("EP - unsupported filter method -> validation error")
    void unsupportedFilterMethodShouldReturnValidationError() {
        SpecificationFilterDTO dto = new SpecificationFilterDTO("id", UUID.randomUUID().toString(), ComparationMethods.LIKE);
        SearchFilterDefinition<Object> definition = SearchFilterDefinition.path(
                "id",
                "id",
                Set.of(ComparationMethods.EQUALS),
                Map.of(ComparationMethods.EQUALS, SearchValueParsers::uuid)
        );

        assertThatThrownBy(() -> ResourceSearchFilterConverter.convert(new SearchDTO(List.of(dto)), Map.of("id", definition)))
                .isInstanceOf(InvalidSearchFilterException.class)
                .hasMessage("Unsupported comparison method LIKE for field id.");
    }

    @Test
    @DisplayName("EP - invalid typed filter value -> validation error")
    void invalidTypedFilterValueShouldReturnValidationError() {
        SpecificationFilterDTO dto = new SpecificationFilterDTO("id", "not-a-uuid", ComparationMethods.EQUALS);
        SearchFilterDefinition<Object> definition = SearchFilterDefinition.path(
                "id",
                "id",
                Set.of(ComparationMethods.EQUALS),
                Map.of(ComparationMethods.EQUALS, SearchValueParsers::uuid)
        );

        assertThatThrownBy(() -> ResourceSearchFilterConverter.convert(new SearchDTO(List.of(dto)), Map.of("id", definition)))
                .isInstanceOf(InvalidSearchFilterException.class)
                .hasMessage("Invalid filter value for id.");
    }

    @Test
    @DisplayName("EP - IN array value -> parsed collection")
    void inArrayValueShouldReturnParsedCollection() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        var array = JsonNodeFactory.instance.arrayNode()
                .add(firstId.toString())
                .add(secondId.toString());

        Object parsed = SearchValueParsers.in(SearchValueParsers::uuid).apply(array);

        assertThat(parsed).isEqualTo(List.of(firstId, secondId));
    }

    @Test
    @DisplayName("EP - scalar value for IN -> validation error")
    void scalarValueForInShouldReturnValidationError() {
        assertThatThrownBy(() -> SearchValueParsers.in(SearchValueParsers::uuid)
                .apply(JsonNodeFactory.instance.textNode(UUID.randomUUID().toString())))
                .isInstanceOf(InvalidSearchFilterException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "@gmail", "gmail.com", "x@example.com"})
    @DisplayName("EP - invalid email LIKE value -> validation error")
    void invalidEmailLikeValueShouldReturnValidationError(String value) {
        assertThatThrownBy(() -> SearchValueParsers.emailLike(JsonNodeFactory.instance.textNode(value)))
                .isInstanceOf(InvalidSearchFilterException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "12-3", "(11)"})
    @DisplayName("EP - invalid phone LIKE value -> validation error")
    void invalidPhoneLikeValueShouldReturnValidationError(String value) {
        assertThatThrownBy(() -> SearchValueParsers.phoneNumberLike(JsonNodeFactory.instance.textNode(value)))
                .isInstanceOf(InvalidSearchFilterException.class);
    }

    @Test
    @DisplayName("EP - LIKE with non-string value -> validation error")
    void likeWithNonStringValueShouldReturnValidationError() {
        assertThatThrownBy(() -> ComparationMethods.LIKE.create("name", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valor para 'LIKE' deve ser uma String.");
    }

    @Test
    @DisplayName("EP - GREATER_THAN_OR_EQUAL with non-comparable value -> validation error")
    void greaterThanOrEqualWithNonComparableValueShouldReturnValidationError() {
        assertThatThrownBy(() -> ComparationMethods.GREATER_THAN_OR_EQUAL.create("metadata", new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valor para 'GREATER_THAN_OR_EQUAL' deve ser Comparable (ex: Instant, LocalDate, Integer, Double).");
    }

    @Test
    @DisplayName("EP - LESS_THAN_OR_EQUAL with non-comparable value -> validation error")
    void lessThanOrEqualWithNonComparableValueShouldReturnValidationError() {
        assertThatThrownBy(() -> ComparationMethods.LESS_THAN_OR_EQUAL.create("metadata", new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valor para 'LESS_THAN_OR_EQUAL' deve ser Comparable.");
    }

    @Test
    @DisplayName("EP - IN with scalar value -> validation error")
    void inWithScalarValueShouldReturnValidationError() {
        assertThatThrownBy(() -> ComparationMethods.IN.create("name", "Maria"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Valor para 'IN' deve ser uma Collection (ex: List, Set).");
    }
}
