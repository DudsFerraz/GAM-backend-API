package br.org.gam.api.shared.persistence;

import br.org.gam.api.shared.domain.GamRG;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GamRGConverterJPA implements AttributeConverter<GamRG, String> {

    @Override
    public String convertToDatabaseColumn(GamRG rg) {
        return rg == null ? null : rg.value();
    }

    @Override
    public GamRG convertToEntityAttribute(String persistedValue) {
        return persistedValue == null ? null : new GamRG(persistedValue);
    }
}
