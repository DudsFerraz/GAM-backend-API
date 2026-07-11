package br.org.gam.api.security.web;

import br.org.gam.api.account.application.useCases.loginAccount.LoginAccount;
import br.org.gam.api.account.application.useCases.registerAccount.RegisterAccount;
import br.org.gam.api.security.application.InvalidTokenFormatException;
import br.org.gam.api.security.application.RefreshTokenExpiredException;
import br.org.gam.api.security.application.TokenNotFoundException;
import br.org.gam.api.security.refreshtoken.application.RefreshTokenService;
import br.org.gam.api.shared.exception.GlobalExceptionHandler;
import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.annotation.UnitTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
@ApiTest
@FunctionalTest
@SecurityTest
@ExtendWith(MockitoExtension.class)
@DisplayName("API contract - Authentication controller")
class AuthControllerContractIT {

    private static final long REFRESH_LIFETIME_MS = 604_800_000L;

    @Mock
    private RegisterAccount registerAccount;

    @Mock
    private LoginAccount loginAccount;

    @Mock
    private RefreshTokenService refreshTokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();
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
    @DisplayName("REQ-AUTH-016 - missing, malformed, unknown, consumed, and expired refresh tokens -> same sign-in-again response")
    void allRefreshFailuresShouldReturnTheSameGenericResponse() throws Exception {
        String malformed = "not-a-uuid";
        String unknown = "11111111-1111-4111-8111-111111111111";
        String consumed = "22222222-2222-4222-8222-222222222222";
        String expired = "33333333-3333-4333-8333-333333333333";

        when(refreshTokenService.refresh(malformed))
                .thenThrow(new InvalidTokenFormatException("malformed"));
        when(refreshTokenService.refresh(unknown))
                .thenThrow(new TokenNotFoundException("unknown"));
        when(refreshTokenService.refresh(consumed))
                .thenThrow(new TokenNotFoundException("already consumed"));
        when(refreshTokenService.refresh(expired))
                .thenThrow(new RefreshTokenExpiredException("expired"));

        List<MvcResult> failures = List.of(
                refreshWithoutCookie(),
                refreshWithCookie(malformed),
                refreshWithCookie(unknown),
                refreshWithCookie(consumed),
                refreshWithCookie(expired)
        );
        Map<String, Object> expected = stableError(failures.get(1));

        assertThat(expected).isNotEmpty();
        assertThat(expected.get("message").toString()).containsIgnoringCase("sign in again");
        assertThat(failures)
                .extracting(MvcResult::getResponse)
                .extracting(response -> response.getStatus())
                .containsOnly(failures.get(1).getResponse().getStatus());
        assertThat(failures).allSatisfy(result ->
                assertThat(stableError(result)).isEqualTo(expected));
    }

    @Test
    @DisplayName("REQ-AUTH-018 - logout after accepted CSRF boundary -> success and expired secure refresh cookie")
    void logoutWithoutAnActiveSessionShouldSucceedAndExpireTheRefreshCookie() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/auth/logout"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isBetween(200, 299);
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains(
                        "refreshToken=",
                        "Max-Age=0",
                        "HttpOnly",
                        "Secure",
                        "SameSite=None",
                        "Path=/"
                );
    }

    private MvcResult refreshWithoutCookie() throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh"))
                .andReturn();
    }

    private MvcResult refreshWithCookie(String refreshToken) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/auth/refresh")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andReturn();
    }

    private Map<String, Object> stableError(MvcResult result) {
        try {
            String body = result.getResponse().getContentAsString();
            if (body.isBlank()) {
                return Map.of();
            }

            Map<String, Object> error = objectMapper.readValue(
                    body,
                    new TypeReference<LinkedHashMap<String, Object>>() { }
            );
            error.remove("timestamp");
            return error;
        } catch (Exception exception) {
            throw new AssertionError("Refresh failure response was not a JSON error body", exception);
        }
    }
}
