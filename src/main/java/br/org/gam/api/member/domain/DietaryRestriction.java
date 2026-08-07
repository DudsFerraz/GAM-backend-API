package br.org.gam.api.member.domain;

import java.util.Objects;

public record DietaryRestriction(InformationStatus status, String details) {
    public DietaryRestriction {
        Objects.requireNonNull(status, "Dietary restriction status cannot be null.");
        details = normalize(details);
        if (status == InformationStatus.YES && details == null) {
            throw new IllegalArgumentException("Dietary restriction details are required when status is YES.");
        }
        if (status != InformationStatus.YES && details != null) {
            throw new IllegalArgumentException("Dietary restriction details must be null unless status is YES.");
        }
    }

    public static DietaryRestriction notInformed() {
        return new DietaryRestriction(InformationStatus.NOT_INFORMED, null);
    }

    static String normalize(String value) {
        if (value == null) return null;
        String normalized = MemberInformationText.trimmed(value);
        if (normalized.isBlank()) return null;
        if (normalized.codePointCount(0, normalized.length()) > 2000) {
            throw new IllegalArgumentException("Details cannot exceed 2,000 Unicode code points.");
        }
        return normalized;
    }
}
