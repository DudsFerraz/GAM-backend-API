package br.org.gam.api.shared.persistence;

import br.org.gam.api.account.persistence.AccountRepository;
import br.org.gam.api.event.persistence.EventRepository;
import br.org.gam.api.member.persistence.MemberRepository;
import br.org.gam.api.member.solicitation.persistence.MembershipSolicitationRepository;
import br.org.gam.api.presence.persistence.PresenceRepository;
import br.org.gam.api.rbac.rolePermission.persistence.RolePermissionRepository;
import br.org.gam.api.shared.activitylog.ActivityLogRepository;
import br.org.gam.api.testing.annotation.StructuralTest;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structural - Ordinary persistence repository boundary")
class BaseRepositoryStructuralTest {

    @Test
    @DisplayName("REQ-PERSISTENCE-005, REQ-PERSISTENCE-009, and ADR-0018 - ordinary repository API -> no multi-row or physical batch-delete operations")
    void ordinaryRepositoryApiShouldNotExposeMultiRowOrPhysicalBatchDeleteOperations() {
        assertThat(Arrays.stream(BaseRepository.class.getMethods())
                .map(BaseRepositoryStructuralTest::signature)
                .distinct())
                .doesNotContain(
                        "deleteAll()",
                        "deleteAll(Iterable)",
                        "deleteAllById(Iterable)",
                        "deleteAllInBatch()",
                        "deleteAllInBatch(Iterable)",
                        "deleteAllByIdInBatch(Iterable)",
                        "deleteInBatch(Iterable)"
                );
    }

    @Test
    @DisplayName("REQ-PERSISTENCE-009 and ADR-0018 - append-only activity repository API -> no deletion operations")
    void appendOnlyActivityRepositoryApiShouldNotExposeDeletionOperations() {
        assertThat(effectiveSignatures(ActivityLogRepository.class))
                .doesNotContain(
                        "delete(Object)",
                        "deleteById(Object)",
                        "deleteAll()",
                        "deleteAll(Iterable)",
                        "deleteAllById(Iterable)",
                        "deleteAllInBatch()",
                        "deleteAllInBatch(Iterable)",
                        "deleteAllByIdInBatch(Iterable)",
                        "deleteInBatch(Iterable)"
                );
    }

    @Test
    @DisplayName("REQ-PERSISTENCE-005, REQ-PERSISTENCE-009, and ADR-0018 - specification repository APIs -> no physical bulk delete")
    void ordinarySpecificationRepositoryApisShouldNotExposePhysicalBulkDelete() {
        assertThat(List.of(
                AccountRepository.class,
                EventRepository.class,
                PresenceRepository.class,
                MemberRepository.class,
                MembershipSolicitationRepository.class,
                RolePermissionRepository.class
        )).allSatisfy(repositoryType ->
                assertThat(effectiveSignatures(repositoryType))
                        .as(repositoryType.getSimpleName())
                        .doesNotContain("delete(Specification)")
        );
    }

    private static List<String> effectiveSignatures(Class<?> repositoryType) {
        return Arrays.stream(repositoryType.getMethods())
                .map(BaseRepositoryStructuralTest::signature)
                .distinct()
                .toList();
    }

    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return method.getName() + "(" + parameters + ")";
    }
}
