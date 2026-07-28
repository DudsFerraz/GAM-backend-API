package br.org.gam.api.shared.web;

import br.org.gam.api.shared.exception.RequestValidationException;
import br.org.gam.api.shared.exception.RequestParameterTypeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PaginationWebConfiguration implements WebMvcConfigurer {

    private static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, Set<String>> SORT_FIELDS = Map.of(
            "/gam-locations", Set.of("name", "city", "state", "countryCode"),
            "/accounts", Set.of("email", "displayName", "createdAt"),
            "/members", Set.of("firstName", "surname", "birthDate", "status"),
            "/events", Set.of("title", "beginDate", "endDate", "type", "status"),
            "/oratorianos", Set.of("oratorioYearAttendances"),
            "/membership-solicitations", Set.of("status", "createdAt", "updatedAt"),
            "/presences", Set.of("createdAt", "updatedAt")
    );

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new PaginationRequestValidator());
    }

    private static class PaginationRequestValidator implements HandlerInterceptor {
        @Override
        public boolean preHandle(
                @NonNull HttpServletRequest request,
                @NonNull HttpServletResponse response,
                @NonNull Object handler
        ) {
            validatePage(request);
            validatePageSize(request);
            validateSort(request);
            return true;
        }

        private void validatePage(HttpServletRequest request) {
            String page = request.getParameter("page");
            if (page == null) {
                return;
            }

            try {
                int parsedPage = Integer.parseInt(page);
                if (parsedPage < 0) {
                    throw new RequestValidationException("query", "page", "RANGE");
                }
            } catch (NumberFormatException exception) {
                throw new RequestParameterTypeException("query", "page", "INTEGER");
            }
        }

        private void validatePageSize(HttpServletRequest request) {
            String size = request.getParameter("size");
            if (size == null) {
                return;
            }

            try {
                int parsedSize = Integer.parseInt(size);
                if (parsedSize < 1 || parsedSize > MAX_PAGE_SIZE) {
                    throw new RequestValidationException("query", "size", "RANGE");
                }
            } catch (NumberFormatException exception) {
                throw new RequestParameterTypeException("query", "size", "INTEGER");
            }
        }

        private void validateSort(HttpServletRequest request) {
            String[] sorts = request.getParameterValues("sort");
            if (sorts == null) {
                return;
            }

            Set<String> allowedFields = allowedSortFields(request.getRequestURI());
            for (String sort : sorts) {
                String[] parts = sort.split(",", -1);
                if (parts.length != 2 || !allowedFields.contains(parts[0])
                        || !("asc".equalsIgnoreCase(parts[1]) || "desc".equalsIgnoreCase(parts[1]))) {
                    throw new RequestValidationException("query", "sort", "ALLOWED_VALUE");
                }
            }
        }

        private Set<String> allowedSortFields(String requestUri) {
            if (requestUri.matches("/events/[^/]+/presences")) {
                return Set.of("memberFirstName", "memberSurname", "registeredAt");
            }
            if (requestUri.matches("/members/[^/]+/presences")) {
                return Set.of("eventBeginDate", "eventTitle", "registeredAt");
            }
            return SORT_FIELDS.entrySet().stream()
                    .filter(entry -> requestUri.startsWith(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(Set.of());
        }
    }
}
