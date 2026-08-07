package br.org.gam.api.member.application;

import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberContributionArea;
import br.org.gam.api.member.domain.MemberExperienceType;
import br.org.gam.api.member.domain.MemberSacramentType;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.StringToClassMapItem;
import io.swagger.v3.oas.annotations.media.Schema;

public final class MemberInformationRDTO {
    private MemberInformationRDTO() {}

    @Schema(requiredProperties = {"experiences", "sacraments"})
    public record ExperiencesAndSacraments(
            @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
                    requiredProperties = {"JORNADA_MISSIONARIA", "CURSO_DE_LIDERANCA", "PASCOA_JUVENIL", "ACAMPABOSCO"},
                    properties = {
                    @StringToClassMapItem(key = "JORNADA_MISSIONARIA", value = InformationStatus.class),
                    @StringToClassMapItem(key = "CURSO_DE_LIDERANCA", value = InformationStatus.class),
                    @StringToClassMapItem(key = "PASCOA_JUVENIL", value = InformationStatus.class),
                    @StringToClassMapItem(key = "ACAMPABOSCO", value = InformationStatus.class)
            })
            Map<MemberExperienceType, InformationStatus> experiences,
            @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
                    requiredProperties = {"BATISMO", "PRIMEIRA_COMUNHAO", "CRISMA"},
                    properties = {
                    @StringToClassMapItem(key = "BATISMO", value = InformationStatus.class),
                    @StringToClassMapItem(key = "PRIMEIRA_COMUNHAO", value = InformationStatus.class),
                    @StringToClassMapItem(key = "CRISMA", value = InformationStatus.class)
            })
            Map<MemberSacramentType, InformationStatus> sacraments
    ) {}

    @Schema(name = "MemberContributionProfileRead", requiredProperties = {
            "contributionAreas", "otherContributionAreas"})
    public record ContributionProfileRead(List<MemberContributionArea> contributionAreas,
                                          List<String> otherContributionAreas) {}

    @Schema(requiredProperties = {"contributionProfile"})
    public record ContributionProfileResponse(ContributionProfileRead contributionProfile) {}
}
