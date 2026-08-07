package br.org.gam.api.member.web;

import br.org.gam.api.member.application.MemberRDTO;
import br.org.gam.api.member.application.useCases.Activation;
import br.org.gam.api.member.application.useCases.GetAnnualMemberInformation;
import br.org.gam.api.member.application.useCases.GetMember;
import br.org.gam.api.member.application.useCases.MemberInformation;
import br.org.gam.api.member.application.useCases.SearchMembers;
import br.org.gam.api.member.application.useCases.registerMember.RegisterMemberWorkflow;
import br.org.gam.api.member.domain.InformationStatus;
import br.org.gam.api.member.domain.MemberStatus;
import br.org.gam.api.presence.application.useCases.GetPresence;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.shared.phonenumber.GamPhoneNumber;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@UnitTest
@FunctionalTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Functional - Member representation consistent-read boundary")
class MemberControllerConsistentReadTest {

    @Mock RegisterMemberWorkflow registerMember;
    @Mock GetMember getMember;
    @Mock SearchMembers searchMembers;
    @Mock Activation activation;
    @Mock GetPresence getPresence;
    @Mock MemberInformation memberInformation;
    @Mock GetAnnualMemberInformation annualMemberInformation;

    @Test
    @DisplayName("REQ-MEMBER-INFO-011 - concurrent mutation between loads -> body and ETag remain from one read boundary")
    void coreBodyAndEtagShouldComeFromOneConsistentReadBoundary() throws Exception {
        UUID memberId = UUID.randomUUID();
        AtomicLong currentVersion = new AtomicLong(41);
        CountDownLatch bodyLoaded = new CountDownLatch(1);
        CountDownLatch mutationCommitted = new CountDownLatch(1);
        MemberRDTO bodyAtVersion41 = new MemberRDTO(
                memberId, null, "Ana", "Silva", LocalDate.of(2000, 1, 1),
                LocalDate.of(2020, 1, 1), "Synthetic City Before", GamPhoneNumber.fromString("+5519998877665"),
                GamEmail.of("ana.consistent-read@example.com"),
                new MemberRDTO.DietaryRestrictionRDTO(InformationStatus.NO, null), MemberStatus.ACTIVE
        );
        when(getMember.byId(memberId)).thenAnswer(ignored -> {
            long loadedVersion = currentVersion.get();
            assertThat(loadedVersion).isEqualTo(41);
            bodyLoaded.countDown();
            assertThat(mutationCommitted.await(5, TimeUnit.SECONDS)).isTrue();
            return bodyAtVersion41;
        });
        when(memberInformation.etag(memberId)).thenAnswer(ignored -> "\"member-" + currentVersion.get() + "\"");
        MemberController controller = new MemberController(registerMember, getMember, searchMembers, activation, getPresence);
        controller.configureMemberInformation(memberInformation, annualMemberInformation);

        Thread concurrentMutation = new Thread(() -> {
            try {
                if (!bodyLoaded.await(5, TimeUnit.SECONDS)) return;
                currentVersion.set(42);
                mutationCommitted.countDown();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        concurrentMutation.start();
        ResponseEntity<MemberRDTO> response = controller.getMemberById(memberId);
        concurrentMutation.join(5_000);

        assertThat(response.getBody()).isEqualTo(bodyAtVersion41);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"member-41\"");
    }
}
