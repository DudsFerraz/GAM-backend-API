package br.org.gam.api.member.application.useCases.registerMember;

import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.shared.domain.GamEmail;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterMemberDTO(
        @NotNull UUID accountId,
        @NotNull @NotBlank String firstName,
        @NotNull @NotBlank String surname,
        @NotNull LocalDate birthDate,
        @NotNull LocalDate gamEntryDate,
        @NotBlank String residentialCity,
        @NotNull GamPhoneNumber phoneNumber,
        @NotNull GamEmail contactEmail,
        @NotNull
        @Schema(
                minLength = 1,
                description = "Leading and trailing Unicode White_Space code points are removed before validation; "
                        + "the normalized reason must contain from 1 through 2,000 Unicode code points."
        )
        String reason
) {
    public RegisterMemberDTO(UUID accountId, String firstName, String surname, LocalDate birthDate,
                             GamPhoneNumber phoneNumber) {
        this(accountId, firstName, surname, birthDate, LocalDate.now(), "Piracicaba", phoneNumber,
                GamEmail.of("member@example.com"), null);
    }

    public RegisterMemberDTO(UUID accountId, String firstName, String surname, LocalDate birthDate,
                             GamPhoneNumber phoneNumber, String reason) {
        this(accountId, firstName, surname, birthDate, LocalDate.now(), "Piracicaba", phoneNumber,
                GamEmail.of("member@example.com"), reason);
    }
}
