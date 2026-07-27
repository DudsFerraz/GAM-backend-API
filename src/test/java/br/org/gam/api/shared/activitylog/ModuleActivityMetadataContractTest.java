package br.org.gam.api.shared.activitylog;

import br.org.gam.api.shared.activitylog.events.ModuleActivity;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@FunctionalTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Functional - Module activity metadata boundary")
class ModuleActivityMetadataContractTest {

    @Mock
    private ActivityLogRepository repository;

    @Mock
    private AuditorAware<UUID> auditorAware;

    private ActivityLogEventListener listener;

    @BeforeEach
    void setUp() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
        listener = new ActivityLogEventListener(new ActivityLogger(repository, auditorAware));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidModuleMetadata")
    @DisplayName("REQ-ACTIVITY-009 - undocumented, personal-text, or primary-target metadata -> rejected")
    void invalidModuleMetadataShouldBeRejectedAtTheModuleEventBoundary(
            String scenario,
            ActivityAction action,
            ActivityTargetType targetType,
            UUID targetId,
            Map<String, Object> metadata
    ) {
        ModuleActivity activity = new ModuleActivity(
                action,
                targetType,
                targetId,
                null,
                null,
                metadata
        );

        assertThatThrownBy(() -> listener.handle(activity))
                .as(scenario)
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allocatedModuleActions")
    @DisplayName("REQ-ACTIVITY-009 - every allocated module action -> undocumented metadata is denied by default")
    void everyAllocatedModuleActionShouldDefaultDenyUndocumentedMetadata(
            String scenario,
            AllocatedModuleAction allocation
    ) {
        assertRejected(
                scenario,
                allocation,
                Map.of("undocumentedFlag", true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allocatedModuleActions")
    @DisplayName("REQ-ACTIVITY-009 - every allocated module action -> arbitrary user-authored text is rejected")
    void everyAllocatedModuleActionShouldRejectUserAuthoredText(
            String scenario,
            AllocatedModuleAction allocation
    ) {
        assertRejected(
                scenario,
                allocation,
                Map.of("notes", "User-authored text must not enter append-only metadata")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allocatedModuleActions")
    @DisplayName("REQ-ACTIVITY-009 - every allocated module action -> primary target duplication is rejected")
    void everyAllocatedModuleActionShouldRejectPrimaryTargetDuplication(
            String scenario,
            AllocatedModuleAction allocation
    ) {
        assertRejected(
                scenario,
                allocation,
                Map.of(allocation.primaryTargetMetadataKey(), allocation.targetId())
        );
    }

    private void assertRejected(
            String scenario,
            AllocatedModuleAction allocation,
            Map<String, Object> metadata
    ) {
        ModuleActivity activity = new ModuleActivity(
                allocation.action(),
                allocation.targetType(),
                allocation.targetId(),
                requiredReason(allocation.action()),
                null,
                metadata
        );

        assertThatThrownBy(() -> listener.handle(activity))
                .as(scenario)
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    private static Stream<Arguments> invalidModuleMetadata() {
        UUID oratorioId = UUID.randomUUID();
        UUID oratorianoId = UUID.randomUUID();
        UUID formId = UUID.randomUUID();
        return Stream.of(
                Arguments.of(
                        "Oratorio metadata duplicates its primary target",
                        ActivityAction.ORATORIO_PLANNING_UPDATED,
                        ActivityTargetType.ORATORIO,
                        oratorioId,
                        Map.of(
                                "oratorioId", oratorioId,
                                "changedFields", List.of("welcomeMessage")
                        )
                ),
                Arguments.of(
                        "Oratoriano metadata copies user-authored personal text",
                        ActivityAction.ORATORIANO_UPDATED,
                        ActivityTargetType.ORATORIANO,
                        oratorianoId,
                        Map.of(
                                "changedFields", List.of("name"),
                                "name", "User-authored full name"
                        )
                ),
                Arguments.of(
                        "Oratoriano form metadata contains an undocumented key",
                        ActivityAction.ORATORIANO_FORM_DETAIL_READ,
                        ActivityTargetType.ORATORIANO_FORM,
                        formId,
                        Map.of("unexpectedFlag", true)
                )
        );
    }

    private static Stream<Arguments> allocatedModuleActions() {
        return Stream.of(
                allocation(ActivityAction.ORATORIO_CREATED, ActivityTargetType.ORATORIO, "eventId"),
                allocation(ActivityAction.ORATORIO_PLANNING_UPDATED, ActivityTargetType.ORATORIO, "oratorioId"),
                allocation(ActivityAction.ORATORIO_TEAM_MEMBER_ASSIGNED, ActivityTargetType.ORATORIO, "oratorioId"),
                allocation(ActivityAction.ORATORIO_TEAM_MEMBER_REMOVED, ActivityTargetType.ORATORIO, "oratorioId"),
                allocation(ActivityAction.ORATORIO_CANCELLED, ActivityTargetType.ORATORIO, "oratorioId"),
                allocation(ActivityAction.ORATORIO_LOCKED, ActivityTargetType.ORATORIO, "oratorioId"),
                allocation(ActivityAction.ORATORIO_FINALIZED, ActivityTargetType.ORATORIO, "oratorioId"),
                allocation(ActivityAction.ORATORIO_REOPENED, ActivityTargetType.ORATORIO, "oratorioId"),
                allocation(ActivityAction.ORATORIO_DELETED, ActivityTargetType.ORATORIO, "oratorioId"),
                allocation(
                        ActivityAction.ORATORIO_MEMBER_ATTENDANCE_REGISTERED,
                        ActivityTargetType.PRESENCE,
                        "presenceId"
                ),
                allocation(
                        ActivityAction.ORATORIO_MEMBER_ATTENDANCE_REMOVED,
                        ActivityTargetType.PRESENCE,
                        "presenceId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_ATTENDANCE_REGISTERED,
                        ActivityTargetType.ORATORIANO_ATTENDANCE,
                        "attendanceId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_ATTENDANCE_REMOVED,
                        ActivityTargetType.ORATORIANO_ATTENDANCE,
                        "attendanceId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_REGISTERED_AND_MARKED_PRESENT,
                        ActivityTargetType.ORATORIANO_ATTENDANCE,
                        "attendanceId"
                ),
                allocation(ActivityAction.ORATORIANO_REGISTERED, ActivityTargetType.ORATORIANO, "oratorianoId"),
                allocation(ActivityAction.ORATORIANO_UPDATED, ActivityTargetType.ORATORIANO, "oratorianoId"),
                allocation(ActivityAction.ORATORIANO_DELETED, ActivityTargetType.ORATORIANO, "oratorianoId"),
                allocation(ActivityAction.ORATORIANO_RESTORED, ActivityTargetType.ORATORIANO, "oratorianoId"),
                allocation(
                        ActivityAction.ORATORIANO_FORM_DRAFT_CREATED,
                        ActivityTargetType.ORATORIANO_FORM,
                        "formId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_FORM_DRAFT_UPDATED,
                        ActivityTargetType.ORATORIANO_FORM,
                        "formId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_FORM_DRAFT_DELETED,
                        ActivityTargetType.ORATORIANO_FORM,
                        "formId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_FORM_COMPLETED,
                        ActivityTargetType.ORATORIANO_FORM,
                        "formId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_FORM_REVOKED,
                        ActivityTargetType.ORATORIANO_FORM,
                        "formId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_FORM_PRINT_SNAPSHOT_CREATED,
                        ActivityTargetType.ORATORIANO_FORM,
                        "formId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_FORM_PDF_RENDERED,
                        ActivityTargetType.ORATORIANO_FORM,
                        "formId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_FORM_DETAIL_READ,
                        ActivityTargetType.ORATORIANO_FORM,
                        "formId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_FORM_ATTACHMENTS_REPLACED,
                        ActivityTargetType.ORATORIANO_FORM,
                        "formId"
                ),
                allocation(
                        ActivityAction.ORATORIANO_FORM_ATTACHMENT_DOWNLOADED,
                        ActivityTargetType.ORATORIANO_FORM,
                        "formId"
                )
        ).map(allocation -> Arguments.of(allocation.action().name(), allocation));
    }

    private static AllocatedModuleAction allocation(
            ActivityAction action,
            ActivityTargetType targetType,
            String primaryTargetMetadataKey
    ) {
        return new AllocatedModuleAction(
                action,
                targetType,
                UUID.randomUUID(),
                primaryTargetMetadataKey
        );
    }

    private static String requiredReason(ActivityAction action) {
        return switch (action) {
            case ORATORIO_CANCELLED, ORATORIO_REOPENED, ORATORIO_DELETED,
                 ORATORIANO_DELETED, ORATORIANO_RESTORED,
                 ORATORIANO_FORM_DRAFT_DELETED, ORATORIANO_FORM_REVOKED ->
                    "Valid audit reason";
            default -> null;
        };
    }

    private record AllocatedModuleAction(
            ActivityAction action,
            ActivityTargetType targetType,
            UUID targetId,
            String primaryTargetMetadataKey
    ) {
    }
}
