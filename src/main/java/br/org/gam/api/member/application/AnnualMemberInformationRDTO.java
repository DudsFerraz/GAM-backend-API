package br.org.gam.api.member.application;

import br.org.gam.api.member.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"id", "surveyCycle", "submittedAt", "occupations", "healthCondition",
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
        @Schema(types = {"string", "null"}) String formationAndMeetingInterests,
        MemberCoordinationInterest coordinationInterest,
        @Schema(types = {"string", "null"}) String additionalComments,
        @Schema(types = {"string", "null"}) String oratorioActivitySuggestions,
        @Schema(types = {"string", "null"}) String instagramPostSuggestions
) {
    @Schema(requiredProperties = {"values", "details"})
    public record Occupations(
            List<MemberOccupation> values,
            @Schema(types = {"string", "null"}) String details
    ) {}

    @Schema(requiredProperties = {"status", "details"})
    public record StatusDetails(
            InformationStatus status,
            @Schema(types = {"string", "null"}) String details
    ) {}
}
