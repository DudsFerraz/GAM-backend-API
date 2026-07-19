package br.org.gam.api.gamLocation.application.useCases;

import br.org.gam.api.gamLocation.application.StrictBigDecimalDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = false)
public record GamLocationMutationDTO(
        @NotNull @NotBlank @Size(min = 1, max = 255) String name,
        @Schema(types = {"string", "null"}, minLength = 1, maxLength = 255) String street,
        @NotNull @NotBlank @Size(min = 1, max = 100) String city,
        @NotNull @NotBlank @Size(min = 1, max = 50) String state,
        @Schema(types = {"string", "null"}, minLength = 1, maxLength = 20) String postalCode,
        @NotNull @NotBlank @Size(min = 2, max = 2) @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
        @JsonDeserialize(using = StrictBigDecimalDeserializer.class)
        @Schema(types = {"number", "null"}, minimum = "-90", maximum = "90", multipleOf = 0.00000001)
        BigDecimal latitude,
        @JsonDeserialize(using = StrictBigDecimalDeserializer.class)
        @Schema(types = {"number", "null"}, minimum = "-180", maximum = "180", multipleOf = 0.00000001)
        BigDecimal longitude
) {
}
