package br.org.gam.api.member.application;

import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.shared.phonenumber.MyPhoneNumber;
import java.time.LocalDate;
import java.util.UUID;

public record MemberRDTO(
        UUID id,
        AccountRDTO account,
        String name,
        LocalDate birthDate,
        MyPhoneNumber phoneNumber,
        MemberStatus status
) {
}
