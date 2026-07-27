package br.org.gam.api.shared.activitylog;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTest
@FunctionalTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Functional - Activity entry validation")
class ActivityLoggerTest {

    @Mock
    private ActivityLogRepository repository;

    @Mock
    private AuditorAware<UUID> auditorAware;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("REQ-ACTIVITY-004 and REQ-ACTIVITY-006 - Account action without trusted actor -> rejected")
    void accountActionWithoutTrustedActorShouldBeRejected() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);

        assertThatThrownBy(() -> logger.log(
                ActivityAction.EVENT_CREATED,
                ActivityTargetType.EVENT,
                UUID.randomUUID(),
                null,
                null,
                validEventCreatedMetadata()
        )).isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("REQ-ACTIVITY-006 and REQ-ACCOUNT-ROLE-007/009 - ordinary Account-role action without Account context -> rejected")
    void ordinaryAccountRoleActionWithoutTrustedAccountActorShouldBeRejected() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);

        assertThatThrownBy(() -> logger.log(
                ActivityAction.ACCOUNT_ROLE_ADDED,
                ActivityTargetType.ACCOUNT_ROLE_ASSIGNMENT,
                UUID.randomUUID(),
                "Direct role assignment",
                null,
                Map.of(
                        "accountId", UUID.randomUUID(),
                        "roleId", UUID.randomUUID(),
                        "systemManaged", false
                )
        )).isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("REQ-ACTIVITY-005 and REQ-EVENT-007 - resource-only Event action with scope target -> rejected")
    void eventCreationWithScopeTargetShouldBeRejected() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);

        assertThatThrownBy(() -> logger.logScope(
                ActivityAction.EVENT_CREATED,
                ActivityTargetType.EVENT,
                "all-events",
                null,
                validEventCreatedMetadata()
        )).isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unicodeWhiteSpaceCodePoints")
    @DisplayName("REQ-ACTIVITY-008 and REQ-PRESENCE-014 - every Unicode White_Space code point around a reason -> removed")
    void requiredReasonShouldRemoveEveryUnicodeWhiteSpaceCodePoint(
            String label,
            int whiteSpaceCodePoint
    ) {
        UUID actorId = UUID.randomUUID();
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(actorId));
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);
        String unicodeWhitespace = new String(Character.toChars(whiteSpaceCodePoint));
        String normalizedReason = "🙏".repeat(2_000);

        assertThatCode(() -> logger.log(
                ActivityAction.PRESENCE_REMOVED,
                ActivityTargetType.PRESENCE,
                UUID.randomUUID(),
                unicodeWhitespace + normalizedReason + unicodeWhitespace,
                null,
                validPresenceRemovedMetadata()
        )).doesNotThrowAnyException();

        ActivityLogEntity saved = captureSavedEntry();
        assertThat(saved.getReason()).isEqualTo(normalizedReason);
        assertThat(saved.getReason().codePointCount(0, saved.getReason().length())).isEqualTo(2_000);
    }

    @Test
    @DisplayName("REQ-ACTIVITY-008 and REQ-PRESENCE-014 - 2,001-code-point required reason -> rejected")
    void requiredReasonOverCodePointLimitShouldBeRejected() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);

        assertThatThrownBy(() -> logger.log(
                ActivityAction.PRESENCE_REMOVED,
                ActivityTargetType.PRESENCE,
                UUID.randomUUID(),
                "🙏".repeat(2_001),
                null,
                validPresenceRemovedMetadata()
        )).isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("REQ-ACTIVITY-008 and REQ-GAM-LOCATION-012 - NONE reason supplied -> rejected")
    void noneModeShouldRejectSuppliedReason() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);

        assertThatThrownBy(() -> logger.log(
                ActivityAction.GAM_LOCATION_CREATED,
                ActivityTargetType.GAM_LOCATION,
                UUID.randomUUID(),
                "not allowed",
                null,
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("REQ-ACTIVITY-005/014 and REQ-PERSISTENCE-012 - deleted-record inspection -> exact scope target and count metadata")
    void deletedRecordInspectionShouldUseExactScopeTargetAndCountMetadata() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);

        logger.logScope(
                ActivityAction.DEVELOPER_VIEWED_SOFT_DELETED_RECORDS,
                ActivityTargetType.EVENT,
                "SOFT_DELETED_RECORDS",
                "Investigate deleted Event records",
                Map.of("count", 0)
        );

        ActivityLogEntity saved = captureSavedEntry();
        assertThat(saved.getActorKind()).isEqualTo(ActivityActorKind.DEVELOPER);
        assertThat(saved.getActorAccountId()).isNull();
        assertThat(saved.getActorReference()).isNotBlank();
        assertThat(saved.getTargetType()).isEqualTo(ActivityTargetType.EVENT);
        assertThat(saved.getTargetId()).isNull();
        assertThat(saved.getTargetScope()).isEqualTo("SOFT_DELETED_RECORDS");
        assertThat(saved.getMetadata()).containsExactlyEntriesOf(Map.of("count", 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDeletedRecordInspectionContracts")
    @DisplayName("REQ-ACTIVITY-005/009/014 and REQ-PERSISTENCE-012 - invalid inspection target or metadata -> rejected")
    void invalidDeletedRecordInspectionContractShouldBeRejected(
            String scenario,
            String targetScope,
            Map<String, Object> metadata
    ) {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.empty());
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);

        assertThatThrownBy(() -> logger.logScope(
                ActivityAction.DEVELOPER_VIEWED_SOFT_DELETED_RECORDS,
                ActivityTargetType.EVENT,
                targetScope,
                "Investigate deleted Event records",
                metadata
        )).as(scenario).isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("REQ-ACTIVITY-008/009 and REQ-EVENT-020 - Event deletion -> required reason and exact fromStatus metadata")
    void eventDeletionShouldAcceptItsExactMetadataSchema() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);

        assertThatCode(() -> logger.log(
                ActivityAction.EVENT_DELETED,
                ActivityTargetType.EVENT,
                UUID.randomUUID(),
                "Remove cancelled community event",
                null,
                Map.of(
                        "type", "GENERIC",
                        "fromStatus", "CANCELLED",
                        "gamLocationId", UUID.randomUUID()
                )
        )).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEventUpdatedMetadata")
    @DisplayName("REQ-ACTIVITY-009 and REQ-EVENT-020 - invalid EVENT_UPDATED closed schema -> rejected")
    void invalidEventUpdatedMetadataShouldBeRejected(String scenario, Map<String, Object> metadata) {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);

        assertThatThrownBy(() -> logger.log(
                ActivityAction.EVENT_UPDATED,
                ActivityTargetType.EVENT,
                UUID.randomUUID(),
                null,
                null,
                metadata
        )).as(scenario).isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("REQ-ACTIVITY-009 and REQ-PRESENCE-006 - prohibited observation text metadata -> rejected")
    void prohibitedObservationTextMetadataShouldBeRejected() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);

        assertThatThrownBy(() -> logger.log(
                ActivityAction.PRESENCE_REGISTERED,
                ActivityTargetType.PRESENCE,
                UUID.randomUUID(),
                null,
                null,
                Map.of(
                        "memberId", UUID.randomUUID(),
                        "eventId", UUID.randomUUID(),
                        "observationsPresent", true,
                        "observations", "Sensitive personal observation"
                )
        )).isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("REQ-ACTIVITY-007 and REQ-WEB-012 - default direct request -> inbound id ignored and UUID v7 stored")
    void applicationGeneratedModeShouldIgnoreInboundRequestIdAndStoreUuidV7() {
        UUID actorId = UUID.randomUUID();
        UUID inboundRequestId = UUID.randomUUID();
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(actorId));
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", inboundRequestId.toString());
        request.addHeader("User-Agent", "must-not-be-persisted");
        request.setRemoteAddr("203.0.113.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        logger.log(
                ActivityAction.EVENT_CREATED,
                ActivityTargetType.EVENT,
                UUID.randomUUID(),
                null,
                null,
                validEventCreatedMetadata()
        );

        ActivityLogEntity saved = captureSavedEntry();
        UUID storedRequestId = UUID.fromString(saved.getRequestId());
        assertThat(storedRequestId).isNotEqualTo(inboundRequestId);
        assertThat(storedRequestId.version()).isEqualTo(7);
        assertThat(saved.getIpAddress()).isNull();
        assertThat(saved.getUserAgent()).isNull();
    }

    @Test
    @DisplayName("REQ-ACTIVITY-007 - non-HTTP activity -> request id absent")
    void nonHttpActivityShouldNotInventARequestId() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(UUID.randomUUID()));
        ActivityLogger logger = new ActivityLogger(repository, auditorAware);

        logger.log(
                ActivityAction.EVENT_CREATED,
                ActivityTargetType.EVENT,
                UUID.randomUUID(),
                null,
                null,
                validEventCreatedMetadata()
        );

        assertThat(captureSavedEntry().getRequestId()).isNull();
    }

    private Map<String, Object> validEventCreatedMetadata() {
        return Map.of(
                "type", "GENERIC",
                "status", "SCHEDULED",
                "gamLocationId", UUID.randomUUID(),
                "requiredPermissionId", UUID.randomUUID()
        );
    }

    private Map<String, Object> validPresenceRemovedMetadata() {
        return Map.of(
                "memberId", UUID.randomUUID(),
                "eventId", UUID.randomUUID(),
                "observationsPresent", false
        );
    }

    private ActivityLogEntity captureSavedEntry() {
        ArgumentCaptor<ActivityLogEntity> captor = ArgumentCaptor.forClass(ActivityLogEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static Stream<Arguments> unicodeWhiteSpaceCodePoints() {
        return Stream.of(
                0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x0020, 0x0085, 0x00A0, 0x1680,
                0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008,
                0x2009, 0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000
        ).map(codePoint -> Arguments.of("U+%04X".formatted(codePoint), codePoint));
    }

    private static Stream<Arguments> invalidDeletedRecordInspectionContracts() {
        return Stream.of(
                Arguments.of("unregistered scope", "ALL_DELETED_ROWS", Map.of("count", 0)),
                Arguments.of("negative count", "SOFT_DELETED_RECORDS", Map.of("count", -1)),
                Arguments.of(
                        "persistence identifier metadata",
                        "SOFT_DELETED_RECORDS",
                        Map.of("count", 1, "table", "events")
                ),
                Arguments.of("missing count", "SOFT_DELETED_RECORDS", Map.of())
        );
    }

    private static Stream<Arguments> invalidEventUpdatedMetadata() {
        return Stream.of(
                Arguments.of("empty changed fields", Map.of("changedFields", List.of())),
                Arguments.of(
                        "unknown changed field",
                        Map.of("changedFields", List.of("unknownField"))
                ),
                Arguments.of(
                        "duplicate changed field",
                        Map.of("changedFields", List.of("title", "title"))
                ),
                Arguments.of(
                        "unstable changed-field order",
                        Map.of("changedFields", List.of("endDate", "title"))
                ),
                Arguments.of(
                        "fromStatus without toStatus",
                        Map.of("changedFields", List.of("endDate"), "fromStatus", "SCHEDULED")
                ),
                Arguments.of(
                        "undocumented metadata key",
                        Map.of("changedFields", List.of("title"), "eventId", UUID.randomUUID())
                )
        );
    }
}
