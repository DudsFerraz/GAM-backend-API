package br.org.gam.api.shared.activitylog;

import br.org.gam.api.shared.persistence.UUIDGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Request-Id";
    static final String REQUEST_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".requestId";

    private final RequestCorrelationMode mode;

    public RequestCorrelationFilter(
            @Value("${gam.request-correlation.mode:APPLICATION_GENERATED}") RequestCorrelationMode mode
    ) {
        this.mode = mode;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        UUID requestId = resolveRequestId(request);
        request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        response.setHeader(HEADER_NAME, requestId.toString());
        filterChain.doFilter(request, response);
    }

    private UUID resolveRequestId(HttpServletRequest request) {
        if (mode == RequestCorrelationMode.TRUSTED_PROXY) {
            UUID trustedRequestId = parseCanonicalUuid(request.getHeader(HEADER_NAME));
            if (trustedRequestId != null) {
                return trustedRequestId;
            }
        }

        return UUIDGenerator.generateUUIDV7();
    }

    private UUID parseCanonicalUuid(String value) {
        if (value == null) {
            return null;
        }

        try {
            UUID requestId = UUID.fromString(value);
            return requestId.toString().equalsIgnoreCase(value) ? requestId : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static UUID requestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        if (value instanceof UUID requestId) {
            return requestId;
        }

        UUID generated = UUIDGenerator.generateUUIDV7();
        request.setAttribute(REQUEST_ATTRIBUTE, generated);
        return generated;
    }
}
