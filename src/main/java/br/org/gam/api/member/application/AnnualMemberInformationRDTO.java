package br.org.gam.api.member.application;

import br.org.gam.api.member.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
        requiredProperties = {"id", "surveyCycle", "submittedAt", "occupations", "healthCondition",
        "religiousVocationConsidered", "massAttendanceFrequency", "saturdayOratorioImpediment",
        "formationAndMeetingInterests", "coordinationInterest", "additionalComments",
        "oratorioActivitySuggestions", "instagramPostSuggestions"})
public record AnnualMemberInformationRDTO(
        UUID id,
        int surveyCycle,
        @Schema(types = {"string", "null"}, format = "date-time") Instant submittedAt,
        Occupations occupations,
        StatusDetails healthCondition,
        InformationStatus religiousVocationConsidered,
        MemberMassAttendanceFrequency massAttendanceFrequency,
        StatusDetails saturdayOratorioImpediment,
        @Schema(types = {"string", "null"}, maxLength = 2000,
                description = "Leading and trailing Unicode White_Space code points are removed and equivalent "
                        + "Unicode representations are normalized before validation; the optional value must "
                        + "contain at most 2,000 (2000) Unicode code points.") String formationAndMeetingInterests,
        MemberCoordinationInterest coordinationInterest,
        @Schema(types = {"string", "null"}, maxLength = 2000,
                description = "Leading and trailing Unicode White_Space code points are removed and equivalent "
                        + "Unicode representations are normalized before validation; the optional value must "
                        + "contain at most 2,000 (2000) Unicode code points.") String additionalComments,
        @Schema(types = {"string", "null"}, maxLength = 2000,
                description = "Leading and trailing Unicode White_Space code points are removed and equivalent "
                        + "Unicode representations are normalized before validation; the optional value must "
                        + "contain at most 2,000 (2000) Unicode code points.") String oratorioActivitySuggestions,
        @Schema(types = {"string", "null"}, maxLength = 2000,
                description = "Leading and trailing Unicode White_Space code points are removed and equivalent "
                        + "Unicode representations are normalized before validation; the optional value must "
                        + "contain at most 2,000 (2000) Unicode code points.") String instagramPostSuggestions
) {
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
            requiredProperties = {"values", "details"})
    public record Occupations(
            @io.swagger.v3.oas.annotations.media.ArraySchema(maxItems = 4, uniqueItems = true)
            List<MemberOccupation> values,
            @Schema(types = {"string", "null"}, maxLength = 2000,
                    description = "Leading and trailing Unicode White_Space code points are removed and equivalent "
                            + "Unicode representations are normalized before validation; the optional value must "
                            + "contain at most 2,000 (2000) Unicode code points.") String details
    ) {}

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
            requiredProperties = {"status", "details"})
    public record StatusDetails(
            InformationStatus status,
            @Schema(types = {"string", "null"}, maxLength = 2000,
                    description = "Leading and trailing Unicode White_Space code points are removed and equivalent "
                            + "Unicode representations are normalized before validation; the optional value must "
                            + "contain at most 2,000 (2000) Unicode code points.") String details
    ) {}
}
