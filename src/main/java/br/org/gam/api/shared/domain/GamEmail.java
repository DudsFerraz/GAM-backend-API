package br.org.gam.api.shared.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record GamEmail(String value) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])$"
    );

    public GamEmail {
        Objects.requireNonNull(value, "email cannot be null");
        value = value.trim().toLowerCase(Locale.ROOT);

        if (value.isBlank()) {
            throw new IllegalArgumentException("email cannot be blank");
        }

        if (!hasPracticalSyntax(value)) {
            throw new IllegalArgumentException("email has invalid syntax");
        }
    }

    @JsonCreator
    public static GamEmail of(String value) {
        return new GamEmail(value);
    }

    @Override
    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    private static boolean hasPracticalSyntax(String value) {
        int atIndex = value.indexOf('@');
        if (atIndex <= 0 || atIndex != value.lastIndexOf('@') || atIndex == value.length() - 1) {
            return false;
        }

        String localPart = value.substring(0, atIndex);
        return !localPart.startsWith(".")
                && !localPart.endsWith(".")
                && !localPart.contains("..")
                && EMAIL_PATTERN.matcher(value).matches();
    }
}
