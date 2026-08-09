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
        @NotBlank
        @Schema(minLength = 1, maxLength = 100,
                description = "Leading and trailing Unicode White_Space code points are removed and internal "
                        + "whitespace sequences are collapsed before validation; the normalized city must contain "
                        + "from 1 through 100 Unicode code points.")
        String residentialCity,
        @NotNull GamPhoneNumber phoneNumber,
        @NotNull GamEmail contactEmail,
        @NotBlank
        @Schema(minLength = 1, maxLength = 2000,
                description = "Trim leading and trailing whitespace before validation; the normalized "
                        + "justification must contain from 1 through 2,000 characters.")
        String justification,
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
