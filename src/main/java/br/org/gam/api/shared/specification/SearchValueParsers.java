package br.org.gam.api.shared.specification;

import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class SearchValueParsers {

    private static final Pattern CANONICAL_LOCAL_DATE =
            Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern CANONICAL_UTC_INSTANT =
            Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T"
                    + "[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?Z");

    private SearchValueParsers() {
    }

    public static String text(JsonNode value) {
        String text = UnicodeWhitespace.trim(scalarText(value));
        if (UnicodeWhitespace.isBlank(text)) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }
        return text;
    }

    public static Function<JsonNode, Object> boundedText(int maximumLength) {
        return value -> {
            String parsed = text(value);
            if (parsed.codePointCount(0, parsed.length()) > maximumLength) {
                throw new InvalidSearchFilterException("Invalid filter value.");
            }
            return parsed;
        };
    }

    public static Object normalizedFullNameLike(JsonNode value) {
        return UnicodeWhitespace.collapse(text(value));
    }

    public static Object humanEquivalentName(JsonNode value) {
        String normalizedSeparators = Normalizer.normalize(text(value), Normalizer.Form.NFC)
                .replaceAll("[\\u2018\\u2019\\u201A\\u201B\\u2032\\u00B4\\u0060]", "'")
                .replaceAll("[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]", "-");
        String normalizedWhitespace = UnicodeWhitespace.collapse(normalizedSeparators);
        return Normalizer.normalize(normalizedWhitespace, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public static Object uuid(JsonNode value) {
        String submitted = text(value);
        java.util.UUID parsed = java.util.UUID.fromString(submitted);
        if (!parsed.toString().equalsIgnoreCase(submitted)) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }
        return parsed;
    }

    public static Object instant(JsonNode value) {
        String submitted = text(value);
        if (!CANONICAL_UTC_INSTANT.matcher(submitted).matches()) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }
        return Instant.parse(submitted);
    }

    public static Object localDate(JsonNode value) {
        String submitted = text(value);
        if (!CANONICAL_LOCAL_DATE.matcher(submitted).matches()) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }
        return LocalDate.parse(submitted);
    }

    public static <E extends Enum<E>> Function<JsonNode, Object> enumValue(Class<E> enumClass) {
        return value -> Enum.valueOf(enumClass, text(value));
    }

    public static Object emailEquals(JsonNode value) {
        return GamEmail.of(text(value));
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
        String normalized = UnicodeWhitespace.collapse(text(value));
        if (!normalized.matches("[0-9\\s+().-]+")) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }
        String digits = normalized.replaceAll("\\D", "");
        if (digits.length() < 4) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }
        return digits;
    }

    public static Function<JsonNode, Object> in(Function<JsonNode, Object> scalarParser) {
        return value -> {
            if (value == null || !value.isArray() || value.isEmpty() || value.size() > 100) {
                throw new InvalidSearchFilterException("Invalid filter value.");
            }

            List<Object> values = new java.util.ArrayList<>();
            value.forEach(item -> values.add(scalarParser.apply(item)));
            return values;
        };
    }

    private static String scalarText(JsonNode value) {
        if (value == null || !value.isTextual()) {
            throw new InvalidSearchFilterException("Invalid filter value.");
        }
        return value.textValue();
    }
}
