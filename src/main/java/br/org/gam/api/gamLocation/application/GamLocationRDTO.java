package br.org.gam.api.gamLocation.application;

import java.math.BigDecimal;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

public record GamLocationRDTO(
        UUID id,
        @Nullable
        @Schema(
                types = {"string", "null"},
                description = "Immutable application-owned code for a system GamLocation."
        )
        String code,
        @Schema(description = "Whether the GamLocation belongs to the system catalog.")
        boolean systemManaged,
        String name,
        @Nullable @Schema(types = {"string", "null"}) String street,
        @Nullable @Schema(types = {"string", "null"}) String city,
        @Nullable @Schema(types = {"string", "null"}) String state,
        @Nullable @Schema(types = {"string", "null"}) String postalCode,
        @Nullable @Schema(types = {"string", "null"}) String countryCode,
        @Nullable @Schema(types = {"number", "null"}) BigDecimal latitude,
        @Nullable @Schema(types = {"number", "null"}) BigDecimal longitude
) {
    public GamLocationRDTO(
            UUID id,
            String name,
            String street,
            String city,
            String state,
            String postalCode,
            String countryCode,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this(
                id,
                null,
                false,
                name,
                street,
                city,
                state,
                postalCode,
                countryCode,
                latitude,
                longitude
        );
    }
}
