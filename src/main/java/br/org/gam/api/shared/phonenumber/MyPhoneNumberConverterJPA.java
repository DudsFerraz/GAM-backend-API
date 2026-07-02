package br.org.gam.api.shared.phonenumber;

import br.org.gam.api.shared.exception.InvalidPhoneNumberException;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

@Converter(autoApply = true)
public class MyPhoneNumberConverterJPA implements AttributeConverter<MyPhoneNumber, String> {
    @Override
    public String convertToDatabaseColumn(MyPhoneNumber myPhoneNumber) {
        if (myPhoneNumber == null) return null;

        return myPhoneNumber.e164format();
    }

    @Override
    public MyPhoneNumber convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;

        try {
            return MyPhoneNumber.parse(dbData, "BR");
        } catch (InvalidPhoneNumberException e) {
            System.err.println("Erro ao converter telefone do DB: " + dbData);
            return null;
        }
    }
}
