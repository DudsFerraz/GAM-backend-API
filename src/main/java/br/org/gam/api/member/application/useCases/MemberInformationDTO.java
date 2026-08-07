package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberContributionArea;
import br.org.gam.api.member.domain.MemberExperienceType;
import br.org.gam.api.member.domain.MemberSacramentType;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class MemberInformationDTO {
    private MemberInformationDTO() {}

    public record Core(@NotBlank String firstName, @NotBlank String surname, @NotNull LocalDate birthDate,
                       @NotBlank String residentialCity, @NotNull GamPhoneNumber phoneNumber,
                       @NotNull GamEmail contactEmail, @NotBlank String reason) {}
    public record GamEntryDate(@NotNull LocalDate gamEntryDate, @NotBlank String reason) {}
    public record DietaryRestriction(@NotNull InformationStatus status, String details, @NotBlank String reason) {}
    public record Experiences(@NotNull Map<MemberExperienceType, InformationStatus> experiences,
                              @NotBlank String reason) {}
    public record Sacraments(@NotNull Map<MemberSacramentType, InformationStatus> sacraments,
                             @NotBlank String reason) {}
    public record ContributionProfile(@NotNull List<MemberContributionArea> contributionAreas,
                                      @NotNull List<String> otherContributionAreas, @NotBlank String reason) {}
}
