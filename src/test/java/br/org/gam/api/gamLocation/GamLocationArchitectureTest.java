package br.org.gam.api.gamLocation;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@StructuralTest
@DisplayName("Structure - Canonical GamLocation terminology")
class GamLocationArchitectureTest {

    private static final Path PRODUCTION_SOURCES = Path.of("src", "main", "java");
    private static final Path LEGACY_LOCATION_PACKAGE = PRODUCTION_SOURCES.resolve(
            Path.of("br", "org", "gam", "api", "location")
    );
    private static final Path MIGRATION_LOCATION_ISOLATION = PRODUCTION_SOURCES.resolve(
            Path.of("br", "org", "gam", "api", "db", "reference", "MigrationLocationIsolation.java")
    );

    @Test
    @DisplayName("REQ-GAM-LOCATION-005 - legacy location package -> no production source types")
    void legacyLocationPackageShouldContainNoProductionSourceTypes() throws IOException {
        assertThat(javaSourcesUnder(LEGACY_LOCATION_PACKAGE)).isEmpty();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-005 - production source -> no legacy location package references")
    void productionSourceShouldNotReferenceLegacyLocationPackage() throws IOException {
        List<Path> offenders;
        try (Stream<Path> sources = Files.walk(PRODUCTION_SOURCES)) {
            offenders = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(GamLocationArchitectureTest::referencesLegacyLocationPackage)
                    .toList();
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("REQ-GAM-LOCATION-005 - physical-place Location types -> canonical GamLocation names")
    void physicalPlaceLocationTypesShouldUseCanonicalGamLocationNames() throws IOException {
        List<Path> offenders;
        try (Stream<Path> sources = Files.walk(PRODUCTION_SOURCES)) {
            offenders = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.getFileName().toString().contains("Location"))
                    .filter(path -> !path.getFileName().toString().contains("GamLocation"))
                    .filter(path -> !path.equals(MIGRATION_LOCATION_ISOLATION))
                    .toList();
        }

        assertThat(offenders).isEmpty();
    }

    private static List<Path> javaSourcesUnder(Path root) throws IOException {
        if (Files.notExists(root)) {
            return List.of();
        }
        try (Stream<Path> sources = Files.walk(root)) {
            return sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static boolean referencesLegacyLocationPackage(Path path) {
        try {
            return Files.readString(path).contains("br.org.gam.api.location");
        } catch (IOException e) {
            throw new IllegalStateException("Could not inspect production source " + path, e);
        }
    }
}
