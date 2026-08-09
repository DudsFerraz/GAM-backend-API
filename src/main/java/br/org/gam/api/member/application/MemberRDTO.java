package br.org.gam.api.member.application;

import br.org.gam.api.account.application.AccountRDTO;
import br.org.gam.api.account.application.AccountSummaryRDTO;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.shared.domain.GamEmail;
import java.time.LocalDate;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE, requiredProperties = {
        "id", "account", "firstName", "surname", "birthDate", "gamEntryDate",
        "residentialCity", "phoneNumber", "contactEmail", "dietaryRestriction", "status"
})
public record MemberRDTO(
        UUID id,
        @Schema(types = {"object", "null"})
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
        DietaryRestrictionRDTO dietaryRestriction,
        MemberStatus status
) {
    public MemberRDTO(UUID id, AccountRDTO account, String name, LocalDate birthDate,
                      GamPhoneNumber phoneNumber, MemberStatus status) {
        this(
                id,
                account == null ? null : new AccountSummaryRDTO(account.id(), account.email(), account.displayName()),
                name,
                null,
                birthDate,
                null,
                null,
                phoneNumber,
                null,
                null,
                status
        );
    }

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
            requiredProperties = {"status", "details"})
    public record DietaryRestrictionRDTO(InformationStatus status,
                                         @Schema(types = {"string", "null"}, maxLength = 2000,
                                                 description = "Leading and trailing Unicode White_Space code points "
                                                         + "are removed and equivalent Unicode representations are "
                                                         + "normalized before validation; the optional value must "
                                                         + "contain at most 2,000 (2000) Unicode code points.") String details) {}
}
