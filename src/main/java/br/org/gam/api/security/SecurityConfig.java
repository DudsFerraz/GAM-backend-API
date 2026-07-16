package br.org.gam.api.security;

import br.org.gam.api.security.application.DelegatedAuthenticationEntryPoint;
import br.org.gam.api.security.jwt.JwtAuthFilter;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

@EnableMethodSecurity
@EnableWebSecurity
@Configuration
public class SecurityConfig {

    private static final Set<String> CSRF_PROTECTED_AUTH_ENDPOINTS = Set.of(
            "/auth/login",
            "/auth/refresh",
            "/auth/logout"
    );

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    private final DelegatedAuthenticationEntryPoint authEntryPoint;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, UserDetailsService userDetailsService, DelegatedAuthenticationEntryPoint authEntryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
        this.authEntryPoint = authEntryPoint;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = Map.of(
                "pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()
        );
        return new DelegatingPasswordEncoder("pbkdf2", encoders);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CanonicalOriginFilter canonicalOriginFilter,
            @Value("${app.auth.cookie.secure:true}") boolean cookieSecure
    ) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName("XSRF-TOKEN");
        csrfTokenRepository.setHeaderName("X-XSRF-TOKEN");
        csrfTokenRepository.setCookiePath("/api/auth");
        csrfTokenRepository.setSecure(cookieSecure);
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(this::requiresCsrfProof))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/docs", "/api/docs/**",
                                "/api/openapi.json", "/api/openapi.json/**", "/api/openapi.json.yaml",
                                "/api/swagger-ui/**", "/swagger-ui/**", "/webjars/**"
                        ).permitAll()
                        .requestMatchers(
                                "/auth/csrf", "/auth/login", "/auth/register", "/auth/refresh", "/auth/logout"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/events/*").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterAfter(canonicalOriginFilter, CsrfFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CanonicalOriginFilter canonicalOriginFilter(
            @Value("${GAM_PUBLIC_ORIGIN}") String publicOrigin,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver
    ) {
        return new CanonicalOriginFilter(publicOrigin, exceptionResolver);
    }

    private boolean requiresCsrfProof(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && CSRF_PROTECTED_AUTH_ENDPOINTS.contains(request.getServletPath());
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
