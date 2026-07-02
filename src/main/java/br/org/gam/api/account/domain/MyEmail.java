package br.org.gam.api.account.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public record MyEmail(
        @NotBlank @Email String value
) {

    public MyEmail {
        if (value != null) {
            value = value.toLowerCase(Locale.ROOT).trim();
        }
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

    @JsonCreator
    public static MyEmail of(String value) {
        return new MyEmail(value);
    }
}
