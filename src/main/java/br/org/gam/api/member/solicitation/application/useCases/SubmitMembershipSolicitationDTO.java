package br.org.gam.api.member.solicitation.application.useCases;

import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.time.LocalDate;

public record SubmitMembershipSolicitationDTO(
        @NotBlank String firstName,
        @NotBlank String surname,
        @NotNull LocalDate birthDate,
        @NotNull GamPhoneNumber phoneNumber,
        @NotBlank String justification,
        @Null(message = "accountId must not be supplied")
        @Schema(hidden = true)
        JsonNode accountId
) {
}
