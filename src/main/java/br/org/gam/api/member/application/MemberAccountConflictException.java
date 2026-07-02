package br.org.gam.api.member.application;

public class MemberAccountConflictException extends RuntimeException {
    public MemberAccountConflictException(String message) {
        super(message);
    }
}
