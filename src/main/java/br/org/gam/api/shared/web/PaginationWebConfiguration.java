package br.org.gam.api.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
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
            "/membership-solicitations", Set.of("status", "createdAt", "updatedAt"),
            "/presences", Set.of("createdAt", "updatedAt")
    );

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PaginationRequestValidator());
    }

    private static class PaginationRequestValidator implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            validatePageSize(request);
            validateSort(request);
            return true;
        }

        private void validatePageSize(HttpServletRequest request) {
            String size = request.getParameter("size");
            if (size == null) {
                return;
            }

            try {
                if (Integer.parseInt(size) > MAX_PAGE_SIZE) {
                    throw new IllegalArgumentException("Page size must not exceed 100.");
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Page size must be a whole number.");
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
                    throw new IllegalArgumentException("Invalid sort parameter.");
                }
            }
        }

        private Set<String> allowedSortFields(String requestUri) {
            return SORT_FIELDS.entrySet().stream()
                    .filter(entry -> requestUri.startsWith(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(Set.of());
        }
    }
}
