package br.org.gam.api.shared.validation;

import br.org.gam.api.shared.activitylog.ActivityReasonNormalizer;
import br.org.gam.api.shared.exception.InvalidCommandException;

public final class RequiredReason {
    private RequiredReason() {
    }

    public static String normalize(String reason, String message) {
        try {
            return ActivityReasonNormalizer.normalizeRequired(reason);
        } catch (IllegalArgumentException exception) {
            throw InvalidCommandException.reason(message);
        }
    }
}
