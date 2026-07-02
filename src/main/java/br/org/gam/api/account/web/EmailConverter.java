package br.org.gam.api.account.web;

import br.org.gam.api.account.domain.MyEmail;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
class EmailConverter implements Converter<String, MyEmail> {
    @Override
    public MyEmail convert(String source) {
        if (source == null || source.isBlank()) return null;

        return new MyEmail(source);
    }
}
