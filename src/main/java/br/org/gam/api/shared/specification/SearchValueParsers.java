package br.org.gam.api.shared.specification;

import br.org.gam.api.account.domain.MyEmail;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class SearchValueParsers {

    private SearchValueParsers() {
    }

    public static String text(JsonNode value) {
        String text = scalarText(value).trim();
        if (text.isBlank()) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }
        return text;
    }

    public static Object uuid(JsonNode value) {
        return java.util.UUID.fromString(text(value));
    }

    public static Object instant(JsonNode value) {
        return Instant.parse(text(value));
    }

    public static Object localDate(JsonNode value) {
        return LocalDate.parse(text(value));
    }

    public static <E extends Enum<E>> Function<JsonNode, Object> enumValue(Class<E> enumClass) {
        return value -> Enum.valueOf(enumClass, text(value).toUpperCase(Locale.ROOT));
    }

    public static Object emailEquals(JsonNode value) {
        return MyEmail.of(text(value));
    }

    public static Object emailLike(JsonNode value) {
        String normalized = text(value).toLowerCase(Locale.ROOT);

        if (normalized.length() < 3 || normalized.startsWith("@")) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }

        int atIndex = normalized.indexOf('@');
        if (atIndex >= 0 && atIndex < 2) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }

        if (atIndex < 0 && normalized.contains(".")) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }

        return normalized;
    }

    public static Object phoneNumberEquals(JsonNode value) {
        return GamPhoneNumber.fromString(text(value));
    }

    public static Object phoneNumberLike(JsonNode value) {
        String digits = text(value).replaceAll("\\D", "");
        if (digits.length() < 4) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }
        return digits;
    }

    public static Function<JsonNode, Object> in(Function<JsonNode, Object> scalarParser) {
        return value -> {
            if (value == null || !value.isArray() || value.isEmpty()) {
                throw new InvalidSearchFilterException("Invalid filter value.");
            }

            List<Object> values = new java.util.ArrayList<>();
            value.forEach(item -> values.add(scalarParser.apply(item)));
            return values;
        };
    }

    private static String scalarText(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode() || value.isArray() || value.isObject()) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }
        return value.asText();
    }
}
