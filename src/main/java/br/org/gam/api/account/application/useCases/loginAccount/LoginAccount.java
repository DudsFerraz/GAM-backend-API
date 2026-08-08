package br.org.gam.api.account.application.useCases.loginAccount;

import br.org.gam.api.security.application.TokensDTO;
import br.org.gam.api.security.jwt.JwtService;
import br.org.gam.api.security.refreshtoken.application.RefreshTokenService;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
public class LoginAccount {
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService accountDetailsService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public LoginAccount(AuthenticationManager authenticationManager, UserDetailsService accountDetailsService, JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.accountDetailsService = accountDetailsService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }
    public TokensDTO login(LoginAccountDTO dto) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        dto.email().value(),
                        dto.password()
                )
        );

        final UserDetails userDetails = accountDetailsService.loadUserByUsername(dto.email().value());

        final String jwt = jwtService.generateToken(userDetails);
        final UUID refreshToken = refreshTokenService.createRefreshToken(dto.email());

        return new TokensDTO(jwt, refreshToken);
    }
}
