package br.org.gam.api.shared.activitylog;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@StructuralTest
@DisplayName("Structural - Typed append-only activity entry")
class ActivityEntryStructureTest {

    @Test
    @DisplayName("REQ-ACTIVITY-003 - persisted envelope -> typed actor and target fields without request fingerprints")
    void persistedEnvelopeShouldMatchTheTypedMinimizedContract() {
        Set<String> fieldNames = Arrays.stream(ActivityLogEntity.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(fieldNames)
                .contains(
                        "id",
                        "occurredAt",
                        "action",
                        "actorKind",
                        "actorAccountId",
                        "actorReference",
                        "targetType",
                        "targetId",
                        "targetScope",
                        "reason",
                        "metadata",
                        "requestId"
                )
                .doesNotContain("summary", "ipAddress", "userAgent");
    }

    @Test
    @DisplayName("REQ-ACTIVITY-012 - committed entry fields -> no public mutation methods")
    void activityEntryShouldNotExposeOrdinaryMutationMethods() {
        Set<String> publicSetters = Arrays.stream(ActivityLogEntity.class.getMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .collect(Collectors.toSet());

        assertThat(publicSetters).isEmpty();
    }
}
