package br.org.gam.api.presence.domain;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.util.Objects;
import java.util.UUID;

public class Presence {
    private UUID id;
    private Member member;
    private Event event;
    private String observations;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO (JPA/MapStruct).</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(Member member, Event event, String observations)}.
     */
    @Deprecated
    public Presence(UUID id, Member member, Event event, String observations) {
        this.id = id;
        this.member = member;
        this.event = event;
        this.observations = observations;
    }

    public static Presence register(Member member, Event event, String observations) {
        Objects.requireNonNull(member, "Present member must not be null");
        Objects.requireNonNull(event, "Presence event must not be null");

        if (observations == null) observations = "";
        observations = observations.trim();

        UUID id = UUIDGenerator.generateUUIDV7();

        return new Presence(id, member, event, observations);
    }


    public Member getMember() {
        return member;
    }

    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getObservations() {
        return observations;
    }
}
