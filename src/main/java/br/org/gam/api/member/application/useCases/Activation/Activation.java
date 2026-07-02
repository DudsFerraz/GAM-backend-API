package br.org.gam.api.member.application.useCases.Activation;

import java.util.UUID;

public interface Activation {
    public void activate(UUID memberId);
    public void deactivate(UUID memberId);
}
