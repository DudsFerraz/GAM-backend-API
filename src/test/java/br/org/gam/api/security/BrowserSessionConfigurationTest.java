package br.org.gam.api.security;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UnitTest
@FunctionalTest
@SecurityTest
@DisplayName("Functional security - browser session configuration")
class BrowserSessionConfigurationTest {

    @Test
    @DisplayName("REQ-WEB-002 - production HTTPS origin and secure cookie -> accepted")
    void productionHttpsOriginAndSecureCookieShouldBeAccepted() {
        BrowserSessionConfiguration configuration = configuration(
                "https://app.example.com", true, "prod");

        assertThatCode(configuration::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REQ-WEB-002 - production HTTPS origin with non-default port -> accepted")
    void productionHttpsOriginWithNonDefaultPortShouldBeAccepted() {
        BrowserSessionConfiguration configuration = configuration(
                "https://app.example.com:8443", true, "prod");

        assertThatCode(configuration::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REQ-WEB-007 - dev loopback HTTP origin and insecure cookie -> accepted")
    void developmentLoopbackHttpOriginAndInsecureCookieShouldBeAccepted() {
        BrowserSessionConfiguration configuration = configuration(
                "http://localhost:3000", false, "dev");

        assertThatCode(configuration::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REQ-WEB-007 - dev IPv6 loopback HTTP origin and insecure cookie -> accepted")
    void developmentIpv6LoopbackHttpOriginAndInsecureCookieShouldBeAccepted() {
        BrowserSessionConfiguration configuration = configuration(
                "http://[::1]:3000", false, "dev");

        assertThatCode(configuration::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REQ-WEB-002 - missing public origin -> startup configuration error")
    void missingPublicOriginShouldFailStartupValidation() {
        BrowserSessionConfiguration configuration = configuration(null, true, "test");

        assertThatThrownBy(configuration::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GAM_PUBLIC_ORIGIN");
    }

    @ParameterizedTest
    @MethodSource("invalidOriginAndCookieCombinations")
    @DisplayName("REQ-WEB-002 and REQ-WEB-007 - invalid origin or cookie combination -> startup configuration error")
    void invalidOriginOrCookieCombinationShouldFailStartupValidation(
            String publicOrigin,
            boolean cookieSecure,
            String activeProfile
    ) {
        BrowserSessionConfiguration configuration = configuration(publicOrigin, cookieSecure, activeProfile);

        assertThatThrownBy(configuration::validate)
                .isInstanceOf(IllegalStateException.class);
    }

    private static Stream<Arguments> invalidOriginAndCookieCombinations() {
        return Stream.of(
                Arguments.of("http://localhost:3000", true, "prod"),
                Arguments.of("http://localhost:3000", false, "prod"),
                Arguments.of("https://app.example.com", false, "prod"),
                Arguments.of("http://app.example.com", false, "dev"),
                Arguments.of("http://192.168.1.10:3000", false, "dev"),
                Arguments.of("https://app.example.com/", true, "prod"),
                Arguments.of("https://app.example.com/path", true, "prod"),
                Arguments.of("https://app.example.com?redirect=/", true, "prod"),
                Arguments.of("https://app.example.com?", true, "prod"),
                Arguments.of("https://app.example.com#", true, "prod"),
                Arguments.of("https://user:password@app.example.com", true, "prod"),
                Arguments.of("https://app.example.com:443", true, "prod"),
                Arguments.of("not-an-origin", true, "prod")
        );
    }

    private static BrowserSessionConfiguration configuration(
            String publicOrigin,
            boolean cookieSecure,
            String activeProfile
    ) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", activeProfile)
                .withProperty("app.auth.cookie.secure", Boolean.toString(cookieSecure));
        if (publicOrigin != null) {
            environment.withProperty("GAM_PUBLIC_ORIGIN", publicOrigin);
        }
        return new BrowserSessionConfiguration(environment, cookieSecure);
    }
}
