package br.org.gam.api.shared.activitylog;

public final class DeveloperActorReference {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();
    private DeveloperActorReference() {
    }

    public static String resolveRequired() {
        String supplied = CURRENT.get();
        if (supplied != null && !supplied.isBlank()) return supplied;
        String reference = System.getProperty("user.name");
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("A trusted Developer actor reference is required.");
        }
        return reference.strip();
    }

    public static void useForCurrentTransaction(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("A trusted Developer actor reference is required.");
        }
        CURRENT.set(reference.strip());
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCompletion(int status) { CURRENT.remove(); }
                    });
        }
    }
}
