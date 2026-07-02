package br.org.gam.api.security;

import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@DisplayName("Security Utilities")
class SecurityUtilsTest {

    private final SecurityUtils securityUtils = new SecurityUtils();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @StructuralTest
    @DisplayName("Structural")
    class Structural {

        @Test
        @DisplayName("missing authentication -> empty authorities")
        void missingAuthenticationShouldReturnEmptyAuthorities() {
            assertThat(securityUtils.getLoggedUserAuthorities()).isEmpty();
        }

        @Test
        @DisplayName("unauthenticated principal -> empty authorities")
        void unauthenticatedPrincipalShouldReturnEmptyAuthorities() {
            TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                    "user",
                    "password",
                    "MEMBER_GET"
            );
            authentication.setAuthenticated(false);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            assertThat(securityUtils.getLoggedUserAuthorities()).isEmpty();
        }

        @Test
        @DisplayName("authenticated principal -> authority names")
        void authenticatedPrincipalShouldReturnAuthorityNames() {
            TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                    "user",
                    "password",
                    java.util.List.of(
                            new SimpleGrantedAuthority("MEMBER_GET"),
                            new SimpleGrantedAuthority("EVENT_SEARCH")
                    )
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            assertThat(securityUtils.getLoggedUserAuthorities()).isEqualTo(Set.of("MEMBER_GET", "EVENT_SEARCH"));
        }
    }
}
