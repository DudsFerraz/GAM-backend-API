package br.org.gam.api.security.web;

import br.org.gam.api.account.application.useCases.loginAccount.LoginAccount;
import br.org.gam.api.account.application.useCases.loginAccount.LoginAccountDTO;
import br.org.gam.api.account.application.useCases.loginAccount.LoginAccountRDTO;
import br.org.gam.api.account.application.useCases.registerAccount.RegisterAccount;
import br.org.gam.api.account.application.useCases.registerAccount.RegisterAccountDTO;
import br.org.gam.api.account.application.useCases.registerAccount.RegisterAccountRDTO;
import br.org.gam.api.security.application.TokenNotFoundException;
import br.org.gam.api.security.application.TokensDTO;
import br.org.gam.api.security.refreshtoken.application.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final RegisterAccount registerAccountService;
    private final LoginAccount loginAccount;
    private final Long refreshTokenExpirationMs;
    private final RefreshTokenService refreshTokenService;
    private final boolean cookieSecure;

    public AuthController(RegisterAccount registerAccountService, LoginAccount loginAccount,
                          @Value("${jwt.refresh-expiration-ms}") Long refreshTokenExpirationMs,
                          RefreshTokenService refreshTokenService,
                          @Value("${app.auth.cookie.secure:true}") boolean cookieSecure) {
        this.registerAccountService = registerAccountService;
        this.loginAccount = loginAccount;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.refreshTokenService = refreshTokenService;
        this.cookieSecure = cookieSecure;
    }

    @Operation(operationId = "registerAccount")
    @PostMapping("/register")
    public ResponseEntity<RegisterAccountRDTO> createAccount(@Valid @RequestBody RegisterAccountDTO dto) {
        RegisterAccountRDTO responseDTO = registerAccountService.register(dto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/accounts/{id}")
                .buildAndExpand(responseDTO.id())
                .toUri();

        return ResponseEntity.created(location).body(responseDTO);
    }

    @Operation(operationId = "login", security = {})
    @PostMapping("/login")
    public ResponseEntity<LoginAccountRDTO> login(@RequestBody @Valid LoginAccountDTO dto, HttpServletResponse response) {

        TokensDTO tokensDto = loginAccount.login(dto);

        setRefreshTokenCookie(response, tokensDto.refreshToken().toString(), refreshTokenExpirationMs);

        return ResponseEntity.ok(
                new LoginAccountRDTO(tokensDto.accessToken())
        );
    }

    @Operation(operationId = "refreshAccessToken", security = {})
    @PostMapping("/refresh")
    public ResponseEntity<LoginAccountRDTO> refreshToken(HttpServletRequest request, HttpServletResponse response) {

        String refreshTokenStr = getRefreshTokenFromCookies(request);
        if (refreshTokenStr == null) {
            throw new TokenNotFoundException("Refresh token was not provided.");
        }

        TokensDTO refreshedTokens = refreshTokenService.refresh(refreshTokenStr);

        setRefreshTokenCookie(response, refreshedTokens.refreshToken().toString(), refreshTokenExpirationMs);

        return ResponseEntity.ok(
                new LoginAccountRDTO(refreshedTokens.accessToken())
        );
    }

    @Operation(operationId = "logout", security = {})
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenStr = getRefreshTokenFromCookies(request);

        refreshTokenService.logout(refreshTokenStr);

        setRefreshTokenCookie(response, "", 0);

        return ResponseEntity.ok(
                "You've been signed out!"
        );
    }

    private String getRefreshTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token, long maxAgeMs) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(maxAgeMs / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

}
