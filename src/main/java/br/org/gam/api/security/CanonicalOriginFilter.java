package br.org.gam.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

final class CanonicalOriginFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/auth/login",
            "/auth/refresh",
            "/auth/logout"
    );

    private final RequestOrigin canonicalOrigin;
    private final HandlerExceptionResolver exceptionResolver;

    CanonicalOriginFilter(String publicOrigin, HandlerExceptionResolver exceptionResolver) {
        this.canonicalOrigin = RequestOrigin.fromOriginHeader(publicOrigin);
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !PROTECTED_PATHS.contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        RequestOrigin sourceOrigin = sourceOrigin(request);
        if (!canonicalOrigin.equals(sourceOrigin)) {
            exceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    new AccessDeniedException("The request origin does not match the canonical public origin.")
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RequestOrigin sourceOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null) {
            return RequestOrigin.fromOriginHeader(origin);
        }

        return RequestOrigin.fromReferer(request.getHeader("Referer"));
    }

    private record RequestOrigin(String scheme, String host, int effectivePort) {

        private static RequestOrigin fromOriginHeader(String value) {
            URI uri = parse(value);
            if (uri == null
                    || uri.getUserInfo() != null
                    || hasText(uri.getRawPath())
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                return null;
            }
            return fromUri(uri);
        }

        private static RequestOrigin fromReferer(String value) {
            return fromUri(parse(value));
        }

        private static RequestOrigin fromUri(URI uri) {
            if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }

            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (port == -1) {
                port = switch (scheme) {
                    case "http" -> 80;
                    case "https" -> 443;
                    default -> -1;
                };
            }
            if (port == -1) {
                return null;
            }

            return new RequestOrigin(scheme, uri.getHost().toLowerCase(Locale.ROOT), port);
        }

        private static URI parse(String value) {
            if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
                return null;
            }
            try {
                return new URI(value);
            } catch (URISyntaxException exception) {
                return null;
            }
        }

        private static boolean hasText(String value) {
            return value != null && !value.isEmpty();
        }
    }
}
