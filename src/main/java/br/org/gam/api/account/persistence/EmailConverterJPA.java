package br.org.gam.api.account.persistence;

import br.org.gam.api.account.domain.MyEmail;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EmailConverterJPA implements AttributeConverter<MyEmail, String> {

    @Override
    public String convertToDatabaseColumn(MyEmail email) {
        if (email == null) return null;

        return email.value();
    }

    @Override
    public MyEmail convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;

        return new MyEmail(dbData);
    }
}
