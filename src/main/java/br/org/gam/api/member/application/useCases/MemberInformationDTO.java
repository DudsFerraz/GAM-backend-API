package br.org.gam.api.member.application.useCases;

import com.fasterxml.jackson.annotation.JsonProperty;
import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberContributionArea;
import br.org.gam.api.member.domain.MemberExperienceType;
import br.org.gam.api.member.domain.MemberSacramentType;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class MemberInformationDTO {
    private MemberInformationDTO() {}

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
            requiredProperties = {"firstName", "surname", "birthDate", "residentialCity",
                    "phoneNumber", "contactEmail", "reason"})
    public record Core(@NotBlank String firstName, @NotBlank String surname, @NotNull LocalDate birthDate,
                       @NotBlank
                       @Schema(minLength = 1, maxLength = 100,
                               description = "Leading and trailing Unicode White_Space code points are removed "
                                       + "and internal whitespace sequences are collapsed before validation; "
                                       + "the normalized city must contain from 1 through 100 Unicode code points.")
                       String residentialCity,
                       @NotNull GamPhoneNumber phoneNumber,
                       @NotNull GamEmail contactEmail,
                       @NotBlank
                       @Schema(minLength = 1, maxLength = 2000,
                               description = "Leading and trailing Unicode White_Space code points are removed "
                                       + "before validation; the normalized reason must contain from 1 through "
                                       + "2,000 Unicode code points.")
                       String reason) {}
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
            requiredProperties = {"gamEntryDate", "reason"})
    public record GamEntryDate(@NotNull LocalDate gamEntryDate,
                               @NotBlank
                               @Schema(minLength = 1, maxLength = 2000,
                                       description = "Leading and trailing Unicode White_Space code points are removed "
                                               + "before validation; the normalized reason must contain from 1 through "
                                               + "2,000 Unicode code points.")
                               String reason) {}
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
            requiredProperties = {"status", "details", "reason"})
    public record DietaryRestriction(@NotNull InformationStatus status,
                                     @JsonProperty(value = "details", required = true)
                                     @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                                             types = {"string", "null"}, maxLength = 2000,
                                             description = "Leading and trailing Unicode White_Space code points are "
                                                     + "removed before validation; a non-null normalized value must "
                                                     + "contain from 1 through 2000 (2,000) Unicode code points.") String details,
                                     @NotBlank
                                     @Schema(minLength = 1, maxLength = 2000,
                                             description = "Leading and trailing Unicode White_Space code points are removed "
                                                     + "before validation; the normalized reason must contain from 1 through "
                                                     + "2,000 Unicode code points.")
                                     String reason) {}
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
            requiredProperties = {"experiences", "reason"})
    public record Experiences(
            @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
                    requiredProperties = {"JORNADA_MISSIONARIA", "CURSO_DE_LIDERANCA",
                            "PASCOA_JUVENIL", "ACAMPABOSCO"},
                    properties = {
                    @StringToClassMapItem(key = "JORNADA_MISSIONARIA", value = InformationStatus.class),
                    @StringToClassMapItem(key = "CURSO_DE_LIDERANCA", value = InformationStatus.class),
                    @StringToClassMapItem(key = "PASCOA_JUVENIL", value = InformationStatus.class),
                    @StringToClassMapItem(key = "ACAMPABOSCO", value = InformationStatus.class)
            })
            Map<MemberExperienceType, InformationStatus> experiences,
                              @NotBlank
                              @Schema(minLength = 1, maxLength = 2000,
                                      description = "Leading and trailing Unicode White_Space code points are removed "
                                              + "before validation; the normalized reason must contain from 1 through "
                                              + "2,000 Unicode code points.")
                              String reason) {}
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
            requiredProperties = {"sacraments", "reason"})
    public record Sacraments(
            @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
                    requiredProperties = {"BATISMO", "PRIMEIRA_COMUNHAO", "CRISMA"},
                    properties = {
                    @StringToClassMapItem(key = "BATISMO", value = InformationStatus.class),
                    @StringToClassMapItem(key = "PRIMEIRA_COMUNHAO", value = InformationStatus.class),
                    @StringToClassMapItem(key = "CRISMA", value = InformationStatus.class)
            })
            Map<MemberSacramentType, InformationStatus> sacraments,
                             @NotBlank
                             @Schema(minLength = 1, maxLength = 2000,
                                     description = "Leading and trailing Unicode White_Space code points are removed "
                                             + "before validation; the normalized reason must contain from 1 through "
                                             + "2,000 Unicode code points.")
                             String reason) {}
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
            requiredProperties = {"contributionAreas", "otherContributionAreas", "reason"})
    public record ContributionProfile(
            @NotNull
            @ArraySchema(maxItems = 18, uniqueItems = true)
            List<MemberContributionArea> contributionAreas,
            @NotNull
            @ArraySchema(maxItems = 10, uniqueItems = true,
                    schema = @Schema(implementation = String.class, minLength = 1, maxLength = 100,
                            description = "Leading and trailing Unicode White_Space is removed and internal whitespace "
                                    + "is collapsed before validation; each normalized value contains from 1 through "
                                    + "100 Unicode code points."))
            List<String> otherContributionAreas,
            @NotBlank
            @Schema(minLength = 1, maxLength = 2000,
                    description = "Leading and trailing Unicode White_Space code points are removed before validation; "
                            + "the normalized reason must contain from 1 through 2,000 Unicode code points.")
            String reason) {}
}
