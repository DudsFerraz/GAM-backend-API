package br.org.gam.api.db.migration;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@StructuralTest
@DisplayName("Structure - System Reference Data Checksum")
class ReferenceDataChecksumStructuralTest {

    private static final Path REPEATABLE_MIGRATION = Path.of(
            "src", "main", "java", "br", "org", "gam", "api", "db", "migration",
            "R__SeedPermissionsAndRoles.java"
    );

    @Test
    @DisplayName("REQ-DATA-002 - complete accepted registry contributes to repeatable checksum")
    void completeRegistryShouldContributeToChecksum() throws IOException {
        String source = Files.readString(REPEATABLE_MIGRATION).replaceAll("\\s+", " ");

        assertThat(source)
                .contains("checksum.update(registryDefinition().getBytes(StandardCharsets.UTF_8));")
                .contains("entries.add(\"role|\" + role.getCode() + \"|\" + role.getDescription());")
                .contains("entries.add(\"permission|\" + permission.getCode() + \"|\" + permission.getLabel() "
                        + "+ \"|\" + permission.getDescription());")
                .contains("entries.add(\"link|\" + role.getCode() + \"|\" + permission.getCode());");
    }
}
