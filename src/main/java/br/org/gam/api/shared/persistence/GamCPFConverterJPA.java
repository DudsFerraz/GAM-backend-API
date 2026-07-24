package br.org.gam.api.shared.persistence;

import br.org.gam.api.shared.domain.GamCPF;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class GamCPFConverterJPA implements AttributeConverter<GamCPF, String> {

    @Override
    public String convertToDatabaseColumn(GamCPF cpf) {
        return cpf == null ? null : cpf.value();
    }

    @Override
    public GamCPF convertToEntityAttribute(String persistedValue) {
        return persistedValue == null ? null : new GamCPF(persistedValue);
    }
}
