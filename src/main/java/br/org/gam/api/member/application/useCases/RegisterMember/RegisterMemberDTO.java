package br.org.gam.api.member.application.useCases.RegisterMember;

import br.org.gam.api.shared.phonenumber.MyPhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterMemberDTO(
        @NotNull UUID accountId,
        @NotNull @NotBlank String firstName,
        @NotNull @NotBlank String surname,
        @NotNull LocalDate birthDate,
        @NotNull MyPhoneNumber phoneNumber
) {
}
