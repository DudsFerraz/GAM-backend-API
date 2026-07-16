package br.org.gam.api.account.application;

import br.org.gam.api.account.application.useCases.getCurrentAccountContext.CurrentAccountContextRDTO;
import br.org.gam.api.account.application.useCases.getCurrentAccountContext.GetCurrentAccountContext;
import br.org.gam.api.testing.annotation.StructuralTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@StructuralTest
@DisplayName("Structure - Current Account Context Package")
class CurrentAccountContextPackageStructuralTest {

    @Test
    @DisplayName("Package guideline - operation with RDTO -> co-located use-case subpackage")
    void currentContextOperationAndResponseShouldBeCoLocated() {
        String expectedPackage =
                "br.org.gam.api.account.application.useCases.getCurrentAccountContext";

        assertThat(List.of(
                GetCurrentAccountContext.class.getPackageName(),
                CurrentAccountContextRDTO.class.getPackageName()
        )).containsOnly(expectedPackage);
    }
}
