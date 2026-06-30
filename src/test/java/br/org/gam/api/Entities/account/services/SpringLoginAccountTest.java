package br.org.gam.api.Entities.account.services;

import br.org.gam.api.Entities.account.myEmail.MyEmail;
import br.org.gam.api.Entities.account.services.loginAccount.LoginAccountDTO;
import br.org.gam.api.Entities.account.services.loginAccount.SpringLoginAccount;
import br.org.gam.api.common.auth.TokensDTO;
import br.org.gam.api.common.auth.jwt.JwtService;
import br.org.gam.api.common.auth.refreshToken.RefreshTokenService;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@UnitTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Login Account Use Case")
class SpringLoginAccountTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserDetailsService accountDetailsService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private SpringLoginAccount loginAccount;

    @Nested
    @FunctionalTest
    @DisplayName("Functional")
    class Functional {

        @Test
        @DisplayName("EP - valid credentials -> access and refresh tokens")
        void validCredentialsShouldReturnTokens() {
            LoginAccountDTO dto = new LoginAccountDTO(MyEmail.of("USER@example.com"), "plain-password");
            UserDetails userDetails = User.withUsername("user@example.com")
                    .password("encoded-password")
                    .roles("USER")
                    .build();
            UUID refreshToken = UUID.randomUUID();

            when(accountDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
            when(jwtService.generateToken(userDetails)).thenReturn("access-token");
            when(refreshTokenService.createRefreshToken(dto.email())).thenReturn(refreshToken);

            TokensDTO tokens = loginAccount.login(dto);

            assertThat(tokens.accessToken()).isEqualTo("access-token");
            assertThat(tokens.refreshToken()).isEqualTo(refreshToken);

            ArgumentCaptor<UsernamePasswordAuthenticationToken> authenticationCaptor =
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager).authenticate(authenticationCaptor.capture());

            UsernamePasswordAuthenticationToken authentication = authenticationCaptor.getValue();
            assertThat(authentication.getPrincipal()).isEqualTo("user@example.com");
            assertThat(authentication.getCredentials()).isEqualTo("plain-password");
            verify(accountDetailsService).loadUserByUsername("user@example.com");
            verify(jwtService).generateToken(userDetails);
            verify(refreshTokenService).createRefreshToken(MyEmail.of("user@example.com"));
        }

        @Test
        @DisplayName("EP - invalid credentials -> authentication error")
        void invalidCredentialsShouldReturnAuthenticationError() {
            LoginAccountDTO dto = new LoginAccountDTO(MyEmail.of("user@example.com"), "wrong-password");

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> loginAccount.login(dto))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Bad credentials");

            verifyNoInteractions(accountDetailsService, jwtService, refreshTokenService);
        }
    }
}
