package br.org.gam.api.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.text.Normalizer;
import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public record Name(
        @Column(name = "first_name", nullable = false, length = FIRST_NAME_MAX_LENGTH) String firstName,
        @Column(name = "surname", nullable = false, length = SURNAME_MAX_LENGTH) String surname

) implements Serializable {
    private static final int FIRST_NAME_MAX_LENGTH = 32;
    private static final int SURNAME_MAX_LENGTH = 64;
    private static final int MIN_LETTERS = 2;
    private static final String INVALID_FORMAT_MESSAGE = "Use only letters, spaces, hyphens, or apostrophes.";
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("[\\p{L} '\\-]+");
    private static final Pattern REPEATED_SEPARATORS = Pattern.compile("[ '\\-]{2,}");

    public Name {
        Objects.requireNonNull(firstName, "First name cannot be null");
        Objects.requireNonNull(surname, "Last name cannot be null");

        firstName = normalizeAndValidate(firstName, "First name", FIRST_NAME_MAX_LENGTH);
        surname = normalizeAndValidate(surname, "Last name", SURNAME_MAX_LENGTH);
    }

    public String getFullName() {
        return this.firstName + " " + this.surname;
    }

    @Override
    public String toString() {
        return getFullName();
    }

    private static String normalizeAndValidate(String value, String fieldName, int maxLength) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("First or last name cannot be blank");
        }

        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(fieldName + " cannot have leading or trailing spaces");
        }

        String normalized = normalizeSeparators(Normalizer.normalize(value.strip(), Normalizer.Form.NFC));

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters");
        }

        if (!ALLOWED_CHARACTERS.matcher(normalized).matches()) {
            throw new IllegalArgumentException(INVALID_FORMAT_MESSAGE);
        }

        if (REPEATED_SEPARATORS.matcher(normalized).find()) {
            throw new IllegalArgumentException(INVALID_FORMAT_MESSAGE);
        }

        if (normalized.startsWith("-") || normalized.endsWith("-")
                || normalized.startsWith("'") || normalized.endsWith("'")) {
            throw new IllegalArgumentException(INVALID_FORMAT_MESSAGE);
        }

        if (countLetters(normalized) < MIN_LETTERS) {
            throw new IllegalArgumentException(fieldName + " must have at least 2 letters");
        }

        return normalized;
    }

    private static String normalizeSeparators(String value) {
        return value
                .replaceAll("[\\u2018\\u2019\\u201A\\u201B\\u2032\\u00B4\\u0060]", "'")
                .replaceAll("[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]", "-");
    }

    private static long countLetters(String value) {
        return value.codePoints()
                .filter(Character::isLetter)
                .count();
    }
}
