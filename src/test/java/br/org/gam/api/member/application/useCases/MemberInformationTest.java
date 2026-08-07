package br.org.gam.api.member.application.useCases;

import br.org.gam.api.member.application.MemberPreconditionException;
import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberContributionArea;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.member.persistence.MemberEntity;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.shared.activitylog.ActivityAction;
import br.org.gam.api.shared.activitylog.ActivityEvents;
import br.org.gam.api.shared.activitylog.ActivityTargetType;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.domain.GamName;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@FunctionalTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Functional - Member information updates")
class MemberInformationTest {

    private static final UUID MEMBER_ID = UUID.fromString("01970000-0000-7000-8000-000000000101");

    @Mock MemberRepository members;
    @Mock ActivityEvents activities;

    @Test
    @DisplayName("REQ-MEMBER-INFO-009/011 - normalized no-op -> unchanged shared ETag and no write or activity")
    void normalizedCoreNoOpShouldPreserveEtagAndActivityHistory() {
        MemberEntity member = syntheticMember();
        when(members.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        MemberInformationDTO.Core dto = new MemberInformationDTO.Core(
                "Ana", "Silva", LocalDate.of(1990, 1, 1), "  Synthetic   City  ",
                GamPhoneNumber.fromString("+5519998877665"), GamEmail.of("ANA.FIXTURE@EXAMPLE.COM"),
                "  Synthetic no-op review  ");

        String resultingEtag = new MemberInformation(members, activities)
                .updateCore(MEMBER_ID, "\"member-7\"", dto);

        assertThat(resultingEtag).isEqualTo("\"member-7\"");
        verify(members, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(activities);
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-011/016 - stale no-op representation -> precondition failure before mutation")
    void staleNoOpShouldFailBeforeMutation() {
        MemberEntity member = syntheticMember();
        when(members.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        MemberInformationDTO.GamEntryDate dto = new MemberInformationDTO.GamEntryDate(
                member.getGamEntryDate(), "Synthetic stale request");

        assertThatThrownBy(() -> new MemberInformation(members, activities)
                .updateGamEntryDate(MEMBER_ID, "\"member-6\"", dto))
                .isInstanceOfSatisfying(MemberPreconditionException.class,
                        failure -> assertThat(failure.getKind()).isEqualTo(MemberPreconditionException.Kind.FAILED));

        assertThat(member.getGamEntryDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        verify(members, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(activities);
    }

    @Test
    @DisplayName("REQ-MEMBER-INFO-010/011 - profile change -> advanced shared ETag and field-only activity metadata")
    void contributionChangeShouldAdvanceSharedEtagAndAuditOnlyChangedFieldNames() {
        MemberEntity member = syntheticMember();
        when(members.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        doAnswer(invocation -> {
            MemberEntity saved = invocation.getArgument(0);
            saved.setVersion(saved.getVersion() + 1);
            return saved;
        }).when(members).saveAndFlush(member);
        MemberInformationDTO.ContributionProfile dto = new MemberInformationDTO.ContributionProfile(
                List.of(MemberContributionArea.FOOTBALL), List.of("Synthetic event cooking"),
                "  Synthetic contribution review  ");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);

        String resultingEtag = new MemberInformation(members, activities)
                .updateContributionProfile(MEMBER_ID, "\"member-7\"", dto);

        assertThat(resultingEtag).isEqualTo("\"member-8\"");
        verify(activities).moduleActivity(
                eq(ActivityAction.MEMBER_CONTRIBUTION_PROFILE_UPDATED), eq(ActivityTargetType.MEMBER),
                eq(MEMBER_ID), eq("Synthetic contribution review"), isNull(), metadata.capture());
        assertThat(metadata.getValue()).containsOnlyKeys("changedFields");
        assertThat(metadata.getValue().get("changedFields"))
                .isEqualTo(List.of("contributionAreas", "otherContributionAreas"));
        assertThat(metadata.getValue().toString())
                .doesNotContain("FOOTBALL", "Synthetic event cooking", "Ana", "Silva");
    }

    private static MemberEntity syntheticMember() {
        MemberEntity member = new MemberEntity();
        member.setId(MEMBER_ID);
        member.setVersion(7);
        member.setName(new GamName("Ana", "Silva"));
        member.setBirthDate(LocalDate.of(1990, 1, 1));
        member.setGamEntryDate(LocalDate.of(2020, 1, 1));
        member.setResidentialCity("Synthetic City");
        member.setPhoneNumber(GamPhoneNumber.fromString("+5519998877665"));
        member.setContactEmail(GamEmail.of("ana.fixture@example.com"));
        member.setDietaryRestrictionStatus(InformationStatus.NOT_INFORMED);
        member.setStatus(MemberStatus.ACTIVE);
        return member;
    }
}
