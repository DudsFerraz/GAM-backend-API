package br.org.gam.api.security;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Validates the public-origin and refresh-cookie combination before the
 * application accepts browser sessions.
 */
@Configuration(proxyBeanMethods = false)
public class BrowserSessionConfiguration {

    private static final String PUBLIC_ORIGIN_PROPERTY = "GAM_PUBLIC_ORIGIN";
    private static final String DEVELOPMENT_PROFILE = "dev";

    private final Environment environment;
    private final boolean cookieSecure;

    public BrowserSessionConfiguration(
            Environment environment,
            @Value("${app.auth.cookie.secure:true}") boolean cookieSecure
    ) {
        this.environment = environment;
        this.cookieSecure = cookieSecure;
    }

    @PostConstruct
    void validateOnStartup() {
        validate();
    }

    void validate() {
        String publicOrigin = environment.getProperty(PUBLIC_ORIGIN_PROPERTY);
        if (publicOrigin == null || publicOrigin.isBlank()) {
            throw new IllegalStateException(
                    "GAM_PUBLIC_ORIGIN must be configured for browser sessions.");
        }

        URI origin = parseOrigin(publicOrigin);
        boolean developmentProfile = environment.acceptsProfiles(Profiles.of(DEVELOPMENT_PROFILE));
        boolean loopback = isLoopbackHost(origin.getHost());
        String scheme = origin.getScheme().toLowerCase(Locale.ROOT);

        if ("https".equals(scheme)) {
            if (!cookieSecure) {
                throw new IllegalStateException(
                        "app.auth.cookie.secure=false is allowed only for a loopback HTTP origin in the dev profile.");
            }
            return;
        }

        if (!"http".equals(scheme) || !developmentProfile || !loopback || cookieSecure) {
            throw new IllegalStateException(
                    "GAM_PUBLIC_ORIGIN must use HTTPS outside the dev loopback HTTP exception, "
                            + "and app.auth.cookie.secure must be false for that exception.");
        }
    }

    private URI parseOrigin(String publicOrigin) {
        URI origin;
        try {
            origin = new URI(publicOrigin);
        } catch (URISyntaxException exception) {
            throw invalidOrigin();
        }

        String scheme = origin.getScheme();
        if (scheme == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || origin.getHost() == null
                || origin.getHost().isBlank()
                || origin.getUserInfo() != null
                || hasText(origin.getRawPath())
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null
                || usesExplicitDefaultPort(origin, scheme)) {
            throw invalidOrigin();
        }

        return origin;
    }

    private boolean usesExplicitDefaultPort(URI origin, String scheme) {
        return ("https".equalsIgnoreCase(scheme) && origin.getPort() == 443)
                || ("http".equalsIgnoreCase(scheme) && origin.getPort() == 80);
    }

    private boolean isLoopbackHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
        }
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "0:0:0:0:0:0:0:1".equals(normalizedHost);
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private IllegalStateException invalidOrigin() {
        return new IllegalStateException(
                "GAM_PUBLIC_ORIGIN must be a valid absolute HTTP(S) origin without path, query, fragment, user info, or an explicit default port.");
    }
}
