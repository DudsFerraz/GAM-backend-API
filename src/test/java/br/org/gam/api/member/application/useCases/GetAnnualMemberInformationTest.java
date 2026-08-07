package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.application.AnnualMemberInformationRDTO;
import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberCoordinationInterest;
import br.org.gam.api.member.domain.MemberMassAttendanceFrequency;
import br.org.gam.api.member.domain.MemberOccupation;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.AnnualMemberInformationResponseEntity;
import br.org.gam.api.member.persistence.AnnualMemberInformationResponseRepository;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.security.SecurityUtils;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import br.org.gam.api.shared.exception.NotFoundException;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@FunctionalTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Functional - Protected annual Member information")
class GetAnnualMemberInformationTest {

    private static final UUID MEMBER_ID = UUID.fromString("01970000-0000-7000-8000-000000000201");
    private static final UUID RESPONSE_ID = UUID.fromString("01970000-0000-7000-8000-000000000202");

    @Mock AnnualMemberInformationResponseRepository responses;
    @Mock SecurityUtils security;
    @Mock ActivityEvents activities;

    @Test
    @DisplayName("REQ-MEMBER-INFO-014/015 - active protected response -> ordered contract and minimized read activity")
    void activeResponseShouldBeAuditedBeforeReturningProtectedContract() {
        AnnualMemberInformationResponseEntity response = syntheticResponse(MemberStatus.ACTIVE);
        when(responses.findByMemberIdAndSurveyCycle(MEMBER_ID, 2026)).thenReturn(Optional.of(response));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);

        AnnualMemberInformationRDTO result =
                new GetAnnualMemberInformation(responses, security, activities).get(MEMBER_ID, 2026);

        verify(activities).moduleActivity(
                eq(ActivityAction.MEMBER_ANNUAL_INFORMATION_READ),
                eq(ActivityTargetType.MEMBER_ANNUAL_INFORMATION_RESPONSE), eq(RESPONSE_ID),
                isNull(), isNull(), metadata.capture());
        assertThat(metadata.getValue()).containsExactlyInAnyOrderEntriesOf(
                Map.of("memberId", MEMBER_ID, "surveyCycle", 2026));
        assertThat(metadata.getValue().toString())
                .doesNotContain("Synthetic health detail", "Synthetic annual comment", "WORK", "OTHER");
        assertThat(result.occupations().values()).containsExactly(MemberOccupation.WORK, MemberOccupation.OTHER);
        assertThat(result.healthCondition().details()).isEqualTo("Synthetic health detail");
        assertThat(result.additionalComments()).isEqualTo("Synthetic annual comment");
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-015 - inactive target without non-active visibility -> hidden and unaudited")
    void hiddenInactiveResponseShouldNotBeDisclosedOrAudited() {
        AnnualMemberInformationResponseEntity response = syntheticResponse(MemberStatus.INACTIVE);
        when(responses.findByMemberIdAndSurveyCycle(MEMBER_ID, 2026)).thenReturn(Optional.of(response));
        when(security.getLoggedUserAuthorities()).thenReturn(Set.of(PermissionEnum.Code.MEMBER_INFORMATION_GET));

        assertThatThrownBy(() ->
                new GetAnnualMemberInformation(responses, security, activities).get(MEMBER_ID, 2026))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(activities);
    }

    private static AnnualMemberInformationResponseEntity syntheticResponse(MemberStatus memberStatus) {
        MemberEntity member = new MemberEntity();
        member.setId(MEMBER_ID);
        member.setStatus(memberStatus);
        AnnualMemberInformationResponseEntity response = new AnnualMemberInformationResponseEntity();
        response.setId(RESPONSE_ID);
        response.setMember(member);
        response.setSurveyCycle(2026);
        response.setSubmittedAt(Instant.parse("2026-02-02T01:28:11Z"));
        response.setOccupations(new LinkedHashSet<>(List.of(MemberOccupation.OTHER, MemberOccupation.WORK)));
        response.setOccupationsDetails("Synthetic occupation detail");
        response.setHealthConditionStatus(InformationStatus.YES);
        response.setHealthConditionDetails("Synthetic health detail");
        response.setReligiousVocationConsidered(InformationStatus.NO);
        response.setMassAttendanceFrequency(MemberMassAttendanceFrequency.WEEKLY);
        response.setSaturdayOratorioImpedimentStatus(InformationStatus.NO);
        response.setCoordinationInterest(MemberCoordinationInterest.MAYBE);
        response.setAdditionalComments("Synthetic annual comment");
        return response;
    }
}
