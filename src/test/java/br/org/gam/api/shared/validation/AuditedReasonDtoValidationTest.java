package br.org.gam.api.shared.validation;

import br.org.gam.api.event.oratorio.application.OratorioApiModels;
import br.org.gam.api.event.application.useCases.manageEvent.EventReasonDTO;
import br.org.gam.api.event.application.useCases.manageEvent.EventReplacementDTO;
import br.org.gam.api.event.application.useCases.manageEvent.ReopenEventDTO;
import br.org.gam.api.event.domain.EventStatus;
import br.org.gam.api.gamLocation.application.useCases.RemoveGamLocationDTO;
import br.org.gam.api.member.application.useCases.CoordinatorTransitionDTO;
import br.org.gam.api.member.application.useCases.DeactivateMemberDTO;
import br.org.gam.api.member.solicitation.application.useCases.ReviewMembershipSolicitationDTO;
import br.org.gam.api.oratoriano.application.OratorianoApiModels.ReplaceOratorianoDTO;
import br.org.gam.api.presence.application.useCases.managePresence.RemovePresenceDTO;
import br.org.gam.api.rbac.accountRole.application.useCases.AddAccountRoleDTO;
import br.org.gam.api.rbac.accountRole.application.useCases.DropAccountRoleDTO;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@FunctionalTest
@DisplayName("Functional - Audited reason DTO validation boundary")
class AuditedReasonDtoValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("auditedReasonDtos")
    @DisplayName("REQ-ACTIVITY-008 - complete White_Space boundary and 2,000 supplementary code points -> DTO validation does not preempt normalization")
    void maximumSupplementaryReasonWithEveryWhiteSpaceBoundaryShouldReachDomainNormalization(
            String scenario,
            Function<String, Object> dtoFactory
    ) {
        String boundary = unicodeWhiteSpaceBoundary();
        String reason = boundary + "🙏".repeat(2_000) + boundary;

        assertThat(validator.validate(dtoFactory.apply(reason)))
                .as(scenario)
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("auditedReasonDtos")
    @DisplayName("REQ-ACTIVITY-008 - U+001C retained content -> DTO validation does not treat it as Unicode White_Space")
    void retainedNonWhiteSpaceControlShouldReachDomainNormalization(
            String scenario,
            Function<String, Object> dtoFactory
    ) {
        assertThat(validator.validate(dtoFactory.apply("\u001C")))
                .as(scenario)
                .isEmpty();
    }

    private static Stream<Arguments> auditedReasonDtos() {
        return Stream.of(
                reasonDto("Account-role add", reason -> new AddAccountRoleDTO(UUID.randomUUID(), reason)),
                reasonDto("Account-role drop", DropAccountRoleDTO::new),
                reasonDto("Event cancellation or deletion", EventReasonDTO::new),
                reasonDto("Event replacement", reason -> new EventReplacementDTO(
                        "Community Event",
                        null,
                        UUID.randomUUID(),
                        null,
                        Instant.parse("2030-01-01T10:00:00Z"),
                        Instant.parse("2030-01-01T11:00:00Z"),
                        reason
                )),
                reasonDto("Event reopening", reason -> new ReopenEventDTO(EventStatus.COMPLETED, reason)),
                reasonDto("GamLocation removal", RemoveGamLocationDTO::new),
                reasonDto("Coordinator transition", CoordinatorTransitionDTO::new),
                reasonDto("Member deactivation", DeactivateMemberDTO::new),
                reasonDto("Membership-solicitation review", ReviewMembershipSolicitationDTO::new),
                reasonDto("Presence removal", RemovePresenceDTO::new),
                reasonDto("Oratorio cancellation or deletion", OratorioApiModels.ReasonDTO::new),
                reasonDto(
                        "Oratorio reopening",
                        reason -> new OratorioApiModels.ReopenDTO(EventStatus.COMPLETED, reason)
                ),
                reasonDto("Oratoriano correction", reason -> new ReplaceOratorianoDTO(
                        "Ana",
                        "Silva",
                        null,
                        null,
                        reason
                )),
                reasonDto(
                        "Oratoriano or form reason command",
                        br.org.gam.api.oratoriano.application.OratorianoApiModels.ReasonDTO::new
                )
        ).map(testCase -> Arguments.of(testCase.scenario(), testCase.factory()));
    }

    private static ReasonDtoCase reasonDto(String scenario, Function<String, Object> factory) {
        return new ReasonDtoCase(scenario, factory);
    }

    private static String unicodeWhiteSpaceBoundary() {
        return Stream.of(
                        0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x0020, 0x0085, 0x00A0, 0x1680,
                        0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008,
                        0x2009, 0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000
                )
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private record ReasonDtoCase(String scenario, Function<String, Object> factory) {
    }
}
