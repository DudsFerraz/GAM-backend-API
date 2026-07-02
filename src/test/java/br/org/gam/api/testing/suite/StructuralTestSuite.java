package br.org.gam.api.testing.suite;

import br.org.gam.api.testing.annotation.StructuralTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages("br.org.gam.api")
@IncludeTags("StructuralTest")
@DisplayName("Suite - Structural Tests")
class StructuralTestSuite {
}
