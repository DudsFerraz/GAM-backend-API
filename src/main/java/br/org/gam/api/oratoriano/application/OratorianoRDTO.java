package br.org.gam.api.oratoriano.application;

import br.org.gam.api.shared.phonenumber.MyPhoneNumber;
import java.time.LocalDate;
import java.util.UUID;

public record OratorianoRDTO(
        UUID id,
        String name,
        LocalDate birthDate,
        MyPhoneNumber phoneNumber
) {
}
