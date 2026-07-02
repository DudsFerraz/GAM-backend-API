package br.org.gam.api.shared.persistence;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@FunctionalTest
@DisplayName("Functional - UUID Generator")
class UUIDGeneratorFunctionalTest {

    @Test
    @DisplayName("EP - generate UUID v7 -> returns version 7 UUID")
    void generateUuidV7ShouldReturnVersion7Uuid() {
        UUID uuid = UUIDGenerator.generateUUIDV7();

        assertThat(uuid).isNotNull();
        assertThat(uuid.version()).isEqualTo(7);
    }

    @Test
    @DisplayName("EP - generate UUID v4 -> returns version 4 UUID")
    void generateUuidV4ShouldReturnVersion4Uuid() {
        UUID uuid = UUIDGenerator.generateUUIDV4();

        assertThat(uuid).isNotNull();
        assertThat(uuid.version()).isEqualTo(4);
    }

    @Test
    @DisplayName("EP - repeated generation -> returns unique UUIDs")
    void repeatedGenerationShouldReturnUniqueUuids() {
        Set<UUID> uuids = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            uuids.add(UUIDGenerator.generateUUIDV7());
            uuids.add(UUIDGenerator.generateUUIDV4());
        }

        assertThat(uuids).hasSize(200);
    }
}
