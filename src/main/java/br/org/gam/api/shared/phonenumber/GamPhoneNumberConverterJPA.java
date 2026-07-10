package br.org.gam.api.shared.phonenumber;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GamPhoneNumberConverterJPA implements AttributeConverter<GamPhoneNumber, String> {
    @Override
    public String convertToDatabaseColumn(GamPhoneNumber phoneNumber) {
        if (phoneNumber == null) return null;

        return phoneNumber.value();
    }

    @Override
    public GamPhoneNumber convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;

        return GamPhoneNumber.fromString(dbData);
    }
}
