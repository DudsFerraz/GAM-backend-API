package br.org.gam.api.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Embeddable
public record GamName(
        @Column(name = "first_name", nullable = false, length = FIRST_NAME_MAX_LENGTH) String firstName,
        @Column(name = "surname", nullable = false, length = SURNAME_MAX_LENGTH) String surname
) implements Serializable {
    private static final int FIRST_NAME_MAX_LENGTH = 32;
    private static final int SURNAME_MAX_LENGTH = 64;
    private static final int MIN_LETTERS = 2;
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("[\\p{L} '\\-]+");
    private static final Pattern REPEATED_OR_COMBINED_SEPARATORS = Pattern.compile("[ '\\-]{2,}");
    private static final Set<String> INTERNAL_PARTICLES = Set.of("de", "da", "do", "das", "dos", "e");

    public GamName {
        Objects.requireNonNull(firstName, "firstName cannot be null");
        Objects.requireNonNull(surname, "surname cannot be null");

        firstName = normalizeAndValidate(firstName, "firstName", FIRST_NAME_MAX_LENGTH);
        surname = normalizeAndValidate(surname, "surname", SURNAME_MAX_LENGTH);
    }

    public String getFullName() {
        return firstName + " " + surname;
    }

    @Override
    public String toString() {
        return getFullName();
    }

    private static String normalizeAndValidate(String rawValue, String fieldName, int maxLength) {
        if (rawValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }

        if (!rawValue.equals(rawValue.strip())) {
            throw new IllegalArgumentException(fieldName + " cannot have leading or trailing whitespace");
        }

        String normalized = normalizeSeparators(Normalizer.normalize(rawValue, Normalizer.Form.NFC));

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " cannot exceed " + maxLength + " characters");
        }

        if (!ALLOWED_CHARACTERS.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " contains invalid characters");
        }

        if (REPEATED_OR_COMBINED_SEPARATORS.matcher(normalized).find()) {
            throw new IllegalArgumentException(fieldName + " contains invalid separators");
        }

        if (isSeparator(normalized.codePointAt(0))
                || isSeparator(normalized.codePointBefore(normalized.length()))) {
            throw new IllegalArgumentException(fieldName + " cannot start or end with a separator");
        }

        if (countLetters(normalized) < MIN_LETTERS) {
            throw new IllegalArgumentException(fieldName + " must contain at least 2 letters");
        }

        if (!normalized.equals(canonicalCapitalization(normalized))) {
            throw new IllegalArgumentException(fieldName + " must use canonical capitalization");
        }

        return normalized;
    }

    private static String canonicalCapitalization(String value) {
        String[] words = value.split(" ", -1);
        StringBuilder canonical = new StringBuilder(value.length());

        for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
            if (wordIndex > 0) {
                canonical.append(' ');
            }

            String word = words[wordIndex];
            if (wordIndex > 0 && INTERNAL_PARTICLES.contains(word.toLowerCase(Locale.ROOT))) {
                canonical.append(word.toLowerCase(Locale.ROOT));
            } else {
                appendCanonicalWord(canonical, word);
            }
        }

        return canonical.toString();
    }

    private static void appendCanonicalWord(StringBuilder canonical, String word) {
        boolean firstLetterOfSegment = true;

        for (int offset = 0; offset < word.length();) {
            int codePoint = word.codePointAt(offset);
            if (codePoint == '-' || codePoint == '\'') {
                canonical.appendCodePoint(codePoint);
                firstLetterOfSegment = true;
            } else {
                canonical.appendCodePoint(firstLetterOfSegment
                        ? Character.toUpperCase(codePoint)
                        : Character.toLowerCase(codePoint));
                firstLetterOfSegment = false;
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static String normalizeSeparators(String value) {
        return value
                .replaceAll("[\\u2018\\u2019\\u201A\\u201B\\u2032\\u00B4\\u0060]", "'")
                .replaceAll("[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]", "-");
    }

    private static boolean isSeparator(int codePoint) {
        return codePoint == ' ' || codePoint == '-' || codePoint == '\'';
    }

    private static long countLetters(String value) {
        return value.codePoints()
                .filter(Character::isLetter)
                .count();
    }
}
