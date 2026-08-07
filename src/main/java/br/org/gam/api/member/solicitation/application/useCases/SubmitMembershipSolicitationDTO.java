package br.org.gam.api.member.solicitation.application.useCases;

import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.shared.domain.GamEmail;
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
        @NotNull LocalDate gamEntryDate,
        @NotBlank String residentialCity,
        @NotNull GamPhoneNumber phoneNumber,
        @NotNull GamEmail contactEmail,
        @NotBlank String justification,
        @Null(message = "accountId must not be supplied")
        @Schema(hidden = true)
        JsonNode accountId
) {
    public SubmitMembershipSolicitationDTO(String firstName, String surname, LocalDate birthDate,
                                           GamPhoneNumber phoneNumber, String justification, JsonNode accountId) {
        this(firstName, surname, birthDate, LocalDate.now(), "Piracicaba", phoneNumber,
                GamEmail.of("member@example.com"), justification, accountId);
    }
}
