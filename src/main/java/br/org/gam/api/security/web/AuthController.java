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
import br.org.gam.api.shared.web.PublicApiUri;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

        return ResponseEntity.created(PublicApiUri.forResource("/accounts/" + responseDTO.id()))
                .body(responseDTO);
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

    @ApiResponse(
            responseCode = "200",
            description = "CSRF proof established.",
            headers = {
                    @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "Prevents storage of the CSRF bootstrap response.",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    @Header(
                            name = HttpHeaders.SET_COOKIE,
                            description = "Host-only XSRF-TOKEN cookie using SameSite=Lax and Path=/api/auth.",
                            schema = @Schema(type = "string")
                    )
            },
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = CsrfBootstrapRDTO.class)
            )
    )
    @Operation(
            operationId = "getCsrfProof",
            security = {},
            summary = "Establish browser CSRF proof",
            description = "Returns a CSRF token and establishes the matching JavaScript-readable XSRF-TOKEN cookie."
    )
    @GetMapping("/csrf")
    public ResponseEntity<CsrfBootstrapRDTO> csrf(CsrfToken csrfToken) {
        CsrfBootstrapRDTO response = new CsrfBootstrapRDTO(
                csrfToken.getToken(),
                csrfToken.getHeaderName()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
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
