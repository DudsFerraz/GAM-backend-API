package br.org.gam.api.testing.suite;

import br.org.gam.api.testing.annotation.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectPackages("br.org.gam.api")
@IncludeTags("IntegrationTest")
@DisplayName("Suite - Integration Tests")
class IntegrationTestSuite {
}
