package br.org.gam.api.shared.phonenumber;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.io.Serializable;

public record GamPhoneNumber(
        String value,
        String nationalFormat,
        int countryCode,
        long nationalNumber
) implements Serializable {
    private static final String DEFAULT_REGION = "BR";

    public GamPhoneNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("phone number cannot be null or blank");
        }
    }

    @JsonCreator
    public static GamPhoneNumber fromString(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            throw new IllegalArgumentException("phone number cannot be null or blank");
        }

        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try {
            PhoneNumber parsedNumber = phoneUtil.parse(rawInput, DEFAULT_REGION);
            if (!phoneUtil.isValidNumber(parsedNumber)) {
                throw new IllegalArgumentException("phone number is not valid or dialable");
            }

            String e164 = phoneUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
            String national = phoneUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
            return new GamPhoneNumber(
                    e164,
                    national,
                    parsedNumber.getCountryCode(),
                    parsedNumber.getNationalNumber()
            );
        } catch (NumberParseException e) {
            throw new IllegalArgumentException("phone number has invalid syntax", e);
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
}
