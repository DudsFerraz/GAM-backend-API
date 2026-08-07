package br.org.gam.api.member.application;

public final class MemberPreconditionException extends RuntimeException {
    public enum Kind { REQUIRED, FAILED, MALFORMED }
    private final Kind kind;

    public MemberPreconditionException(Kind kind) {
        super(switch (kind) {
            case REQUIRED -> "If-Match is required.";
            case FAILED -> "The Member representation is stale.";
            case MALFORMED -> "If-Match must contain exactly one strong Member ETag.";
        });
        this.kind = kind;
    }

    public Kind getKind() { return kind; }
}
