package br.org.gam.api.shared.phonenumber;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class MyPhoneNumberConverter implements Converter<String, MyPhoneNumber> {
    @Override
    public MyPhoneNumber convert(String source) {
        if (source == null || source.isBlank()) return null;

        return MyPhoneNumber.parse(source, "BR");
    }
}
