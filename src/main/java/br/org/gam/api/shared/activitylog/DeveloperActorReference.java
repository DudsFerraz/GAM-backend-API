package br.org.gam.api.shared.activitylog;

public final class DeveloperActorReference {
    private DeveloperActorReference() {
    }

    public static String resolveRequired() {
        String reference = System.getProperty("user.name");
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("A trusted Developer actor reference is required.");
        }
        return reference.strip();
    }
}
