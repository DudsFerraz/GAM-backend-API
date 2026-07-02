package br.org.gam.api.security.web;

import br.org.gam.api.account.application.useCases.LoginAccount.LoginAccount;
import br.org.gam.api.account.application.useCases.LoginAccount.LoginAccountDTO;
import br.org.gam.api.account.application.useCases.LoginAccount.LoginAccountRDTO;
import br.org.gam.api.account.application.useCases.RegisterAccount.RegisterAccount;
import br.org.gam.api.account.application.useCases.RegisterAccount.RegisterAccountDTO;
import br.org.gam.api.account.application.useCases.RegisterAccount.RegisterAccountRDTO;
import br.org.gam.api.security.application.TokensDTO;
import br.org.gam.api.security.refreshtoken.application.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private final RegisterAccount registerAccountService;
    private final LoginAccount loginAccount;
    private final Long refreshTokenExpirationMs;
    private final RefreshTokenService refreshTokenService;
    private final Boolean isCookieSecure;

    public AuthController(RegisterAccount registerAccountService, LoginAccount loginAccount,
                          @Value("${jwt.refresh-expiration-ms}") Long refreshTokenExpirationMs,
                          RefreshTokenService refreshTokenService,
                          @Value("${app.auth.cookie.secure}") Boolean isCookieSecure) {
        this.registerAccountService = registerAccountService;
        this.loginAccount = loginAccount;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
        this.refreshTokenService = refreshTokenService;
        this.isCookieSecure = isCookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterAccountRDTO> createAccount(@Valid @RequestBody RegisterAccountDTO dto) {
        RegisterAccountRDTO responseDTO = registerAccountService.register(dto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(responseDTO.id())
                .toUri();

        return ResponseEntity.created(location).body(responseDTO);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginAccountRDTO> login(@RequestBody @Valid LoginAccountDTO dto, HttpServletResponse response) {

        TokensDTO tokensDto = loginAccount.login(dto);

        setRefreshTokenCookie(response, tokensDto.refreshToken().toString(), refreshTokenExpirationMs);

        return ResponseEntity.ok(
                new LoginAccountRDTO(tokensDto.accessToken())
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginAccountRDTO> refreshToken(HttpServletRequest request, HttpServletResponse response) {

        String refreshTokenStr = getRefreshTokenFromCookies(request);
        if (refreshTokenStr == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        TokensDTO refreshedTokens = refreshTokenService.refresh(refreshTokenStr);

        setRefreshTokenCookie(response, refreshedTokens.refreshToken().toString(), refreshTokenExpirationMs);

        return ResponseEntity.ok(
                new LoginAccountRDTO(refreshedTokens.accessToken())
        );
    }

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
            if ("refreshToken".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token, long maxAgeMs) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", token)
                .httpOnly(true)
                .secure(isCookieSecure)
                .path("/")
                .maxAge(maxAgeMs / 1000)
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

}
