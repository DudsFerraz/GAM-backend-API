package br.org.gam.api.security.web;

import br.org.gam.api.account.application.useCases.loginAccount.LoginAccount;
import br.org.gam.api.account.application.useCases.registerAccount.RegisterAccount;
import br.org.gam.api.security.application.TokensDTO;
import br.org.gam.api.security.refreshtoken.application.RefreshTokenService;
import br.org.gam.api.shared.exception.GlobalExceptionHandler;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@UnitTest
@StructuralTest
@ExtendWith(MockitoExtension.class)
@DisplayName("Structural - Authentication controller")
class AuthControllerStructuralTest {

    private static final long REFRESH_LIFETIME_MS = 604_800_000L;

    @Mock
    private RegisterAccount registerAccount;

    @Mock
    private LoginAccount loginAccount;

    @Mock
    private RefreshTokenService refreshTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(
                registerAccount,
                loginAccount,
                REFRESH_LIFETIME_MS,
                refreshTokenService,
                true
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("REQ-AUTH-014 - unrelated cookie before refreshToken -> named cookie is selected")
    void unrelatedCookieBeforeRefreshTokenShouldNotBlockRefresh() throws Exception {
        UUID refreshToken = UUID.randomUUID();
        when(refreshTokenService.refresh(refreshToken.toString()))
                .thenReturn(new TokensDTO("access-token", UUID.randomUUID()));

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh")
                        .cookie(
                                new Cookie("unrelated", "value"),
                                new Cookie("refreshToken", refreshToken.toString())
                        ))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("access-token");
    }

    @Test
    @DisplayName("REQ-AUTH-014 - unrelated cookies without refreshToken -> refresh is rejected")
    void unrelatedCookiesWithoutRefreshTokenShouldRejectRefresh() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh")
                        .cookie(new Cookie("unrelated", "value")))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotEqualTo(200);
    }
}
