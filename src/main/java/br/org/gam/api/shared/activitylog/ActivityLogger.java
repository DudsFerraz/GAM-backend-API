package br.org.gam.api.shared.activitylog;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class ActivityLogger {
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final ActivityLogRepository activityLogRepository;
    private final AuditorAware<UUID> auditorAware;

    public ActivityLogger(ActivityLogRepository activityLogRepository, AuditorAware<UUID> auditorAware) {
        this.activityLogRepository = activityLogRepository;
        this.auditorAware = auditorAware;
    }

    public void log(ActivityAction action, ActivityTargetType targetType, UUID targetId,
                    String reason, String summary, Map<String, Object> metadata) {
        ActivityLogEntity activity = new ActivityLogEntity();
        activity.setActorAccountId(auditorAware.getCurrentAuditor().orElse(null));
        activity.setAction(action);
        activity.setTargetType(targetType);
        activity.setTargetId(targetId);
        activity.setReason(blankToNull(reason));
        activity.setSummary(blankToNull(summary));
        activity.setMetadata(metadata == null ? Map.of() : metadata);

        currentRequest().ifPresent(request -> {
            activity.setRequestId(blankToNull(request.getHeader(REQUEST_ID_HEADER)));
            activity.setIpAddress(blankToNull(request.getRemoteAddr()));
            activity.setUserAgent(blankToNull(request.getHeader("User-Agent")));
        });

        activityLogRepository.save(activity);
    }

    private Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Optional.of(attributes.getRequest());
        }
        return Optional.empty();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
