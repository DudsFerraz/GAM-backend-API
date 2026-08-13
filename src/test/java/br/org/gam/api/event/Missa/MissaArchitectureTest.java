package br.org.gam.api.event.missa;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@StructuralTest
@DisplayName("Structure - Missa application and pre-production boundaries")
class MissaArchitectureTest {

    private static final Path MISSA_SOURCES = Path.of(
            "src", "main", "java", "br", "org", "gam", "api", "event", "missa"
    );
    private static final Path USE_CASES = MISSA_SOURCES.resolve(Path.of("application", "useCases"));
    private static final Set<String> EXPECTED_OPERATIONS = Set.of(
            "CreateMissa.java",
            "GetMissa.java",
            "ReplaceMissa.java",
            "AssignMissaMember.java",
            "RemoveMissaMember.java",
            "LockMissa.java",
            "FinalizeMissa.java",
            "ReopenMissa.java",
            "CancelMissa.java",
            "DeleteMissa.java"
    );

    @Test
    @DisplayName("Application guideline and REQ-MISSA-020 - specialized actions and read -> focused use cases")
    void missaWorkflowShouldUseFocusedActionAndReadComponents() throws IOException {
        assertThat(USE_CASES.resolve("MissaOperations.java")).doesNotExist();
        assertThat(USE_CASES.resolve("MissaWorkflowSupport.java"))
                .as("focused Missa use cases must own their workflows instead of delegating to a monolith")
                .doesNotExist();

        try (Stream<Path> sources = Files.walk(USE_CASES)) {
            List<Path> operationSources = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> EXPECTED_OPERATIONS.contains(path.getFileName().toString()))
                    .toList();

            assertThat(operationSources.stream().map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrderElementsOf(EXPECTED_OPERATIONS);
            assertThat(operationSources)
                    .allSatisfy(path -> assertThat(readSource(path))
                            .as("focused workflow ownership in %s", path.getFileName())
                            .doesNotContain("MissaWorkflowSupport"));
        }
    }

    @Test
    @DisplayName("Application guideline - DTO/RDTO source -> no responsibility-cardinality business policy")
    void missaDataObjectsShouldContainNoResponsibilityCardinalityPolicy() throws IOException {
        Path models = MISSA_SOURCES.resolve(Path.of("application", "MissaApiModels.java"));
        String source = Files.exists(models) ? Files.readString(models) : "";

        assertThat(source)
                .doesNotContain("enum Responsibility")
                .doesNotContain("singleMember");
    }

    @Test
    @DisplayName("REQ-MISSA-002/004 and pre-production policy - production Missa source -> no legacy assignment-at-creation seam")
    void productionMissaSourceShouldContainNoLegacyAssignmentSeam() throws IOException {
        List<String> forbidden = List.of(
                "comentariosMember",
                "leitura1Member",
                "leitura2Member",
                "salmoMember",
                "precesMember",
                "acolhidaMembers",
                "missa_acolhida_members"
        );

        try (Stream<Path> sources = Files.walk(MISSA_SOURCES)) {
            List<Path> offenders = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> containsAny(path, forbidden))
                    .toList();
            assertThat(offenders).isEmpty();
        }
    }

    private static boolean containsAny(Path path, List<String> values) {
        try {
            String source = Files.readString(path);
            return values.stream().anyMatch(source::contains);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect Missa source " + path, exception);
        }
    }

    private static String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect Missa source " + path, exception);
        }
    }
}
