package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.application.AnnualMemberInformationRDTO;
import br.org.gam.api.member.domain.MemberOccupation;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.AnnualMemberInformationResponseEntity;
import br.org.gam.api.member.persistence.AnnualMemberInformationResponseRepository;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import br.org.gam.api.shared.exception.NotFoundException;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetAnnualMemberInformation {
    private final AnnualMemberInformationResponseRepository responses;
    private final SecurityUtils security;
    private final ActivityEvents activities;

    public GetAnnualMemberInformation(AnnualMemberInformationResponseRepository responses,
                                      SecurityUtils security, ActivityEvents activities) {
        this.responses = responses;
        this.security = security;
        this.activities = activities;
    }

    @Transactional
    public AnnualMemberInformationRDTO get(UUID memberId, int surveyCycle) {
        AnnualMemberInformationResponseEntity response = responses.findByMemberIdAndSurveyCycle(memberId, surveyCycle)
                .orElseThrow(() -> NotFoundException.resource("AnnualMemberInformationResponse", memberId));
        if (response.getMember().getStatus() == MemberStatus.INACTIVE
                && !security.getLoggedUserAuthorities().contains(PermissionEnum.Code.MEMBER_GET_NON_ACTIVE)) {
            throw NotFoundException.resource("AnnualMemberInformationResponse", memberId);
        }
        activities.moduleActivity(ActivityAction.MEMBER_ANNUAL_INFORMATION_READ,
                ActivityTargetType.MEMBER_ANNUAL_INFORMATION_RESPONSE, response.getId(), null, null,
                Map.of("memberId", memberId, "surveyCycle", surveyCycle));
        return toRdto(response);
    }

    private AnnualMemberInformationRDTO toRdto(AnnualMemberInformationResponseEntity value) {
        return new AnnualMemberInformationRDTO(value.getId(), value.getSurveyCycle(), value.getSubmittedAt(),
                new AnnualMemberInformationRDTO.Occupations(Arrays.stream(MemberOccupation.values())
                        .filter(value.getOccupations()::contains).toList(), value.getOccupationsDetails()),
                new AnnualMemberInformationRDTO.StatusDetails(value.getHealthConditionStatus(), value.getHealthConditionDetails()),
                value.getReligiousVocationConsidered(), value.getMassAttendanceFrequency(),
                new AnnualMemberInformationRDTO.StatusDetails(value.getSaturdayOratorioImpedimentStatus(),
                        value.getSaturdayOratorioImpedimentDetails()), value.getFormationAndMeetingInterests(),
                value.getCoordinationInterest(), value.getAdditionalComments(), value.getOratorioActivitySuggestions(),
                value.getInstagramPostSuggestions());
    }
}
