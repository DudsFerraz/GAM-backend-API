package br.org.gam.api.shared.activitylog;

import br.org.gam.api.shared.activitylog.events.MemberStatusChangedActivity;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;

@UnitTest
@FunctionalTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Functional - Account-less Member lifecycle activity metadata")
class AccountlessMemberActivityMetadataTest {

    @Mock ActivityLogger logger;

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-002 - Account-less activation -> explicit empty Role-change collections")
    void accountlessActivationShouldPersistExplicitEmptyRoleCollections() {
        UUID memberId = UUID.randomUUID();
        ActivityLogEventListener listener = new ActivityLogEventListener(logger);

        listener.handle(new MemberStatusChangedActivity(
                ActivityAction.MEMBER_ACTIVATED, memberId, null,
                "INACTIVE", "ACTIVE", null, null, "Synthetic activation"
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(logger).log(eq(ActivityAction.MEMBER_ACTIVATED), eq(ActivityTargetType.MEMBER), eq(memberId),
                eq("Synthetic activation"), isNull(), metadata.capture());
        assertThat(metadata.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "previousStatus", "INACTIVE",
                "newStatus", "ACTIVE",
                "rolesAdded", List.of(),
                "rolesRemoved", List.of()
        ));
    }

    @Test
    @DisplayName("REQ-MEMBER-IMPORT-002 - Account-less deactivation -> explicit empty Role-change collections")
    void accountlessDeactivationShouldPersistExplicitEmptyRoleCollections() {
        UUID memberId = UUID.randomUUID();
        ActivityLogEventListener listener = new ActivityLogEventListener(logger);

        listener.handle(new MemberStatusChangedActivity(
                ActivityAction.MEMBER_DEACTIVATED, memberId, null,
                "ACTIVE", "INACTIVE", null, null, "Synthetic deactivation"
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(logger).log(eq(ActivityAction.MEMBER_DEACTIVATED), eq(ActivityTargetType.MEMBER), eq(memberId),
                eq("Synthetic deactivation"), isNull(), metadata.capture());
        assertThat(metadata.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "previousStatus", "ACTIVE",
                "newStatus", "INACTIVE",
                "rolesAdded", List.of(),
                "rolesRemoved", List.of()
        ));
    }
}
