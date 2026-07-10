package br.org.gam.api.shared.phonenumber;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class GamPhoneNumberConverter implements Converter<String, GamPhoneNumber> {
    @Override
    public GamPhoneNumber convert(String source) {
        return GamPhoneNumber.fromString(source);
    }
}
