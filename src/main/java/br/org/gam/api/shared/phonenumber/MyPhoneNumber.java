package br.org.gam.api.shared.phonenumber;

import br.org.gam.api.shared.exception.InvalidPhoneNumberException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.io.Serializable;

public record MyPhoneNumber(
        String e164format,
        String nationalFormat,
        int countryCode,
        long nationalNumber
) implements Serializable {

    public static MyPhoneNumber parse(String rawInput, String defaultRegion) throws InvalidPhoneNumberException {
        if (rawInput == null || rawInput.isBlank()) {
            throw new InvalidPhoneNumberException("Phone number cannot be null or blank.");
        }

        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        try {
            PhoneNumber proto = phoneUtil.parse(rawInput, defaultRegion);

            if (!phoneUtil.isValidNumber(proto)) {
                throw new InvalidPhoneNumberException("Invalid phone number.");
            }

            String e164 = phoneUtil.format(proto, PhoneNumberUtil.PhoneNumberFormat.E164);
            String national = phoneUtil.format(proto, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
            int code = proto.getCountryCode();
            long number = proto.getNationalNumber();

            return new MyPhoneNumber(e164, national, code, number);

        } catch (NumberParseException e) {
            throw new InvalidPhoneNumberException("Unrecognizable phone number format.", e);
        }
    }

    @JsonCreator
    public static MyPhoneNumber fromString(String rawInput) {
        try {
            return MyPhoneNumber.parse(rawInput, "BR");
        } catch (InvalidPhoneNumberException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    @JsonValue
    public String toString() {
        return e164format;
    }

}
