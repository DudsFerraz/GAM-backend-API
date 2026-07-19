package br.org.gam.api.gamLocation.application;

import br.org.gam.api.shared.exception.InvalidCommandException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class GamLocationNormalizer {
    private static final Set<String> ISO_ALPHA_2_CODES = Set.of(Locale.getISOCountries());

    private GamLocationNormalizer() {
    }

    public static Values normalize(
            String name,
            String street,
            String city,
            String state,
            String postalCode,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        String normalizedName = requiredText(name, "name", 255);
        String normalizedStreet = optionalText(street, "street", 255);
        String normalizedCity = requiredText(city, "city", 100);
        String normalizedState = requiredText(state, "state", 50);
        String normalizedPostalCode = optionalText(postalCode, "postalCode", 20);
        String normalizedCountryCode = countryCode(countryCode);
        coordinates(latitude, longitude);

        return new Values(
                normalizedName,
                normalizedStreet,
                normalizedCity,
                normalizedState,
                normalizedPostalCode,
                normalizedCountryCode,
                latitude,
                longitude,
                canonical(normalizedName),
                canonicalAbsent(normalizedStreet),
                canonical(normalizedCity),
                canonical(normalizedState),
                canonicalAbsent(normalizedPostalCode),
                canonical(normalizedCountryCode)
        );
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null) {
            invalid(field + " is required.");
        }
        String normalized = value.strip();
        validateSingleLine(normalized, field);
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > maxLength) {
            invalid(field + " must contain between 1 and " + maxLength + " characters after trimming.");
        }
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        validateSingleLine(normalized, field);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            invalid(field + " must not exceed " + maxLength + " characters after trimming.");
        }
        return normalized;
    }

    private static String countryCode(String value) {
        if (value == null) {
            invalid("countryCode is required.");
        }
        String normalized = value.strip();
        if (!normalized.matches("[A-Za-z]{2}")) {
            invalid("countryCode must contain exactly two letters.");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!ISO_ALPHA_2_CODES.contains(normalized)) {
            invalid("countryCode must be a recognized ISO 3166-1 alpha-2 code.");
        }
        return normalized;
    }

    private static void coordinates(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            invalid("latitude and longitude must be supplied together or omitted together.");
        }
        if (latitude == null) {
            return;
        }
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0) {
            invalid("latitude must be between -90 and 90.");
        }
        if (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            invalid("longitude must be between -180 and 180.");
        }
        if (latitude.scale() > 8 || longitude.scale() > 8) {
            invalid("coordinates must have at most eight fractional digits.");
        }
    }

    private static void validateSingleLine(String value, String field) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                invalid(field + " must be a single-line value without control characters.");
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static String canonicalAbsent(String value) {
        return value == null ? "" : canonical(value);
    }

    public static String canonical(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(withoutAccents.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < withoutAccents.length();) {
            int codePoint = withoutAccents.codePointAt(offset);
            if (Character.isWhitespace(codePoint)) {
                pendingSpace = result.length() > 0;
            } else {
                if (pendingSpace) {
                    result.append(' ');
                    pendingSpace = false;
                }
                result.appendCodePoint(codePoint);
            }
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static void invalid(String message) {
        throw InvalidCommandException.reason(message);
    }

    public record Values(
            String name,
            String street,
            String city,
            String state,
            String postalCode,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude,
            String identityName,
            String identityStreet,
            String identityCity,
            String identityState,
            String identityPostalCode,
            String identityCountryCode
    ) {
    }
}
