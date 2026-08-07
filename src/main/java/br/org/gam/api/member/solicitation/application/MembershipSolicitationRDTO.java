package br.org.gam.api.member.solicitation.application;

import br.org.gam.api.account.application.AccountSummaryRDTO;
import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.shared.domain.GamEmail;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MembershipSolicitationRDTO(
        UUID id,
        AccountSummaryRDTO account,
        String firstName,
        String surname,
        LocalDate birthDate,
        LocalDate gamEntryDate,
        String residentialCity,
        GamPhoneNumber phoneNumber,
        GamEmail contactEmail,
        String justification,
        MembershipSolicitationStatus status,
        Instant submittedAt,
        AccountSummaryRDTO reviewedBy,
        Instant decidedAt,
        String reviewReason,
        UUID memberId
) {
}
