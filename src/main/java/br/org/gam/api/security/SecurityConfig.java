package br.org.gam.api.security;

import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.security.application.DelegatedAccessDeniedHandler;
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
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
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
    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            UserDetailsService userDetailsService,
            DelegatedAuthenticationEntryPoint authEntryPoint
    ) {
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
            DelegatedAccessDeniedHandler accessDeniedHandler,
            @Value("${app.auth.cookie.secure:true}") boolean cookieSecure
    ) throws Exception {
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookieName("XSRF-TOKEN");
        csrfTokenRepository.setHeaderName("X-XSRF-TOKEN");
        csrfTokenRepository.setCookiePath("/api/auth");
        csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                .secure(cookieSecure)
                .sameSite("Lax"));

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
                        .requestMatchers(HttpMethod.POST, "/accounts/search")
                        .hasAuthority(PermissionEnum.Code.ACCOUNT_SEARCH)
                        .requestMatchers(HttpMethod.POST, "/members/search")
                        .hasAuthority(PermissionEnum.Code.MEMBER_SEARCH)
                        .requestMatchers(HttpMethod.POST, "/members")
                        .hasAuthority(PermissionEnum.Code.MEMBER_MANAGE)
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/members/*/coordinator/grant",
                                "/members/*/coordinator/revoke"
                        )
                        .hasAuthority(PermissionEnum.Code.COORDINATOR_MANAGE)
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/members/*/oratorio-coordinator/grant",
                                "/members/*/oratorio-coordinator/revoke"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIO_COORD_MANAGE)
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/members/*/activate",
                                "/members/*/deactivate"
                        )
                        .hasAuthority(PermissionEnum.Code.MEMBER_ACTIVATION)
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/membership-solicitations/*/approve",
                                "/membership-solicitations/*/reject"
                        )
                        .hasAuthority(PermissionEnum.Code.MEMBER_MANAGE)
                        .requestMatchers(HttpMethod.POST, "/accounts/*/roles")
                        .hasAuthority(PermissionEnum.Code.ACCOUNT_ROLE_MANAGE)
                        .requestMatchers(HttpMethod.PATCH, "/accounts/*/roles/*/drop")
                        .hasAuthority(PermissionEnum.Code.ACCOUNT_ROLE_MANAGE)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/accounts/*/roles",
                                "/accounts/*/role-assignments/*"
                        )
                        .hasAuthority(PermissionEnum.Code.ACCOUNT_GET)
                        .requestMatchers(HttpMethod.GET, "/roles/*/permissions")
                        .access(new WebExpressionAuthorizationManager(
                                "hasAuthority('" + PermissionEnum.Code.ROLE_GET + "') and hasAuthority('"
                                        + PermissionEnum.Code.PERMISSION_GET + "')"
                        ))
                        .requestMatchers(HttpMethod.GET, "/roles", "/roles/*")
                        .hasAuthority(PermissionEnum.Code.ROLE_GET)
                        .requestMatchers(HttpMethod.GET, "/permissions/*")
                        .hasAuthority(PermissionEnum.Code.PERMISSION_GET)
                        .requestMatchers(HttpMethod.POST, "/events/search")
                        .hasAuthority(PermissionEnum.Code.EVENT_SEARCH)
                        .requestMatchers(HttpMethod.POST, "/events")
                        .hasAuthority(PermissionEnum.Code.EVENT_CREATE)
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/events/*"
                        )
                        .hasAuthority(PermissionEnum.Code.EVENT_MANAGE)
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/events/*/lock",
                                "/events/*/finalize",
                                "/events/*/reopen",
                                "/events/*/cancel"
                        )
                        .hasAuthority(PermissionEnum.Code.EVENT_MANAGE)
                        .requestMatchers(HttpMethod.DELETE, "/events/*")
                        .hasAuthority(PermissionEnum.Code.EVENT_MANAGE)
                        .requestMatchers(HttpMethod.POST, "/events/*/presences")
                        .hasAuthority(PermissionEnum.Code.PRESENCE_REGISTER)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/events/*/presences",
                                "/events/*/presences/*"
                        )
                        .hasAuthority(PermissionEnum.Code.EVENT_GET_PRESENCES)
                        .requestMatchers(HttpMethod.PATCH, "/events/*/presences/*")
                        .hasAuthority(PermissionEnum.Code.PRESENCE_EDIT)
                        .requestMatchers(HttpMethod.DELETE, "/events/*/presences/*")
                        .hasAuthority(PermissionEnum.Code.PRESENCE_REMOVE)
                        .requestMatchers(HttpMethod.POST, "/gam-locations")
                        .hasAuthority(PermissionEnum.Code.GAM_LOCATION_CREATE)
                        .requestMatchers(HttpMethod.GET, "/gam-locations", "/gam-locations/*")
                        .hasAuthority(PermissionEnum.Code.GAM_LOCATION_GET)
                        .requestMatchers(HttpMethod.PUT, "/gam-locations/*")
                        .hasAuthority(PermissionEnum.Code.GAM_LOCATION_MANAGE)
                        .requestMatchers(HttpMethod.DELETE, "/gam-locations/*")
                        .hasAuthority(PermissionEnum.Code.GAM_LOCATION_MANAGE)
                        .requestMatchers(
                                HttpMethod.POST,
                                "/oratorios/*/attendance/oratorianos/register-and-mark"
                        )
                        .access(new WebExpressionAuthorizationManager(
                                "hasAuthority('" + PermissionEnum.Code.ORATORIO_ATTENDANCE_MANAGE
                                        + "') and hasAuthority('" + PermissionEnum.Code.ORATORIANO_REGISTER + "')"
                        ))
                        .requestMatchers(HttpMethod.POST, "/oratorios")
                        .hasAuthority(PermissionEnum.Code.ORATORIO_CREATE)
                        .requestMatchers(HttpMethod.GET, "/oratorios/*")
                        .hasAuthority(PermissionEnum.Code.ORATORIO_GET)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/oratorios/*/attendance/members",
                                "/oratorios/*/attendance/oratorianos",
                                "/oratorios/*/attendance/present"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIO_ATTENDANCE_GET)
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/oratorios/*/planning",
                                "/oratorios/*/teams/*/members/*"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIO_MANAGE)
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/oratorios/*/attendance/members/*",
                                "/oratorios/*/attendance/oratorianos/*"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIO_ATTENDANCE_MANAGE)
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/oratorios/*",
                                "/oratorios/*/teams/*/members/*"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIO_MANAGE)
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/oratorios/*/attendance/members/*",
                                "/oratorios/*/attendance/oratorianos/*"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIO_ATTENDANCE_MANAGE)
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/oratorios/*/lock",
                                "/oratorios/*/finalize",
                                "/oratorios/*/reopen",
                                "/oratorios/*/cancel"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIO_MANAGE)
                        .requestMatchers(HttpMethod.POST, "/oratorianos/search")
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_GET)
                        .requestMatchers(HttpMethod.POST, "/oratorianos")
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_REGISTER)
                        .requestMatchers(HttpMethod.POST, "/oratorianos/*/forms")
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_FORM_MANAGE)
                        .requestMatchers(HttpMethod.POST, "/oratorianos/*/forms/*/print-snapshots")
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_FORM_PDF_GENERATE)
                        .requestMatchers(HttpMethod.GET, "/oratorianos/*/forms/*/print-snapshots")
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_FORM_PDF_GENERATE)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/oratorianos/*/forms/*/print-snapshots/*/pdf"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_FORM_PDF_GENERATE)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/oratorianos/*/forms/*/signed-attachments"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_FORM_ATTACHMENT_GET)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/oratorianos/*/forms/*/signed-attachments/*"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_FORM_ATTACHMENT_GET)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/oratorianos/*/forms",
                                "/oratorianos/*/forms/*"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_FORM_GET)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/oratorianos/*",
                                "/oratorianos/*/attendances",
                                "/oratorianos/*/attendance-summary"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_GET)
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/oratorianos/*/forms/*",
                                "/oratorianos/*/forms/*/signed-attachments"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_FORM_MANAGE)
                        .requestMatchers(HttpMethod.PUT, "/oratorianos/*")
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_MANAGE)
                        .requestMatchers(HttpMethod.DELETE, "/oratorianos/*/forms/*")
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_FORM_MANAGE)
                        .requestMatchers(HttpMethod.DELETE, "/oratorianos/*")
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_MANAGE)
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/oratorianos/*/forms/*/complete",
                                "/oratorianos/*/forms/*/revoke"
                        )
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_FORM_MANAGE)
                        .requestMatchers(HttpMethod.PATCH, "/oratorianos/*/restore")
                        .hasAuthority(PermissionEnum.Code.ORATORIANO_MANAGE)
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
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
