package br.org.gam.api.gamLocation.application;

import java.math.BigDecimal;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

public record GamLocationRDTO(
        UUID id,
        String name,
        @Nullable @Schema(types = {"string", "null"}) String street,
        String city,
        String state,
        @Nullable @Schema(types = {"string", "null"}) String postalCode,
        String countryCode,
        @Nullable @Schema(types = {"number", "null"}) BigDecimal latitude,
        @Nullable @Schema(types = {"number", "null"}) BigDecimal longitude
) {
}
