package br.org.gam.api.security;

import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@SecurityTest
@StructuralTest
@DisplayName("Security - PBKDF2 password encoding")
class PasswordEncoderSecurityTest {

    private static final String RAW_PASSWORD = "password-for-encoder-tests";

    @Test
    @DisplayName("new password -> PBKDF2 marker and successful match")
    void newPasswordShouldUsePbkdf2AndMatch() {
        PasswordEncoder encoder = passwordEncoder();

        String encoded = encoder.encode(RAW_PASSWORD);

        assertThat(encoded).startsWith("{pbkdf2}");
        assertThat(encoder.matches(RAW_PASSWORD, encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }

    private PasswordEncoder passwordEncoder() {
        return new SecurityConfig(null, null, null).passwordEncoder();
    }
}
