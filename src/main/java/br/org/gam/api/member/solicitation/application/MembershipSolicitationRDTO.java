package br.org.gam.api.member.solicitation.application;

import br.org.gam.api.account.application.AccountSummaryRDTO;
import br.org.gam.api.member.solicitation.domain.MembershipSolicitationStatus;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.shared.domain.GamEmail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        requiredProperties = {
                "id", "account", "firstName", "surname", "birthDate", "gamEntryDate",
                "residentialCity", "phoneNumber", "contactEmail", "justification", "status",
                "submittedAt", "reviewedBy", "decidedAt", "reviewReason", "memberId"
        })
public record MembershipSolicitationRDTO(
        UUID id,
        AccountSummaryRDTO account,
        String firstName,
        String surname,
        LocalDate birthDate,
        LocalDate gamEntryDate,
        @Schema(minLength = 1, maxLength = 100,
                description = "Leading and trailing Unicode White_Space code points are removed and internal "
                        + "whitespace sequences are collapsed before validation; the normalized city must "
                        + "contain from 1 through 100 Unicode code points.")
        String residentialCity,
        GamPhoneNumber phoneNumber,
        GamEmail contactEmail,
        @Schema(minLength = 1, maxLength = 2000,
                description = "Leading and trailing Unicode White_Space code points are removed before validation; "
                        + "the normalized justification must contain from 1 through 2,000 Unicode code points.")
        String justification,
        MembershipSolicitationStatus status,
        Instant submittedAt,
        @Schema(types = {"object", "null"})
        AccountSummaryRDTO reviewedBy,
        @Schema(types = {"string", "null"}, format = "date-time")
        Instant decidedAt,
        @Schema(types = {"string", "null"}, minLength = 1, maxLength = 2000,
                description = "Leading and trailing Unicode White_Space code points are removed before validation; "
                        + "the normalized review reason must contain from 1 through 2,000 Unicode code points.")
        String reviewReason,
        @Schema(types = {"string", "null"}, format = "uuid")
        UUID memberId
) {
}
