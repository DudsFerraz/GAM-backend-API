package br.org.gam.api.location.application;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.lang.Nullable;

public record LocationRDTO(
        UUID id,
        String name,
        @Nullable String street,
        String city,
        String state,
        @Nullable String postalCode,
        String countryCode,
        @Nullable BigDecimal latitude,
        @Nullable BigDecimal longitude
) {
}
