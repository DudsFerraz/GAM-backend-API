package br.org.gam.api.event.missa.domain;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.member.domain.Member;
import java.util.*;

public class Missa {
    private UUID id;
    private Event event;
    private Member comentariosMember;
    private Member leitura1Member;
    private Member salmoMember;
    private Member leitura2Member;
    private Member precesMember;
    private Set<Member> acolhidaMembers;

    @Deprecated
    public Missa(UUID id, Event event, Member comentariosMember, Member leitura1Member, Member salmoMember, Member leitura2Member, Member precesMember, Set<Member> acolhidaMembers) {
        this.id = id;
        this.event = event;
        this.comentariosMember = comentariosMember;
        this.leitura1Member = leitura1Member;
        this.salmoMember = salmoMember;
        this.leitura2Member = leitura2Member;
        this.precesMember = precesMember;
        this.acolhidaMembers = acolhidaMembers;
    }

    public static Missa register(Event event, Member comentariosMember, Member leitura1Member, Member salmoMember, Member leitura2Member, Member precesMember, Set<Member> acolhidaMembers ){
        Objects.requireNonNull(event, "event cannot be null");

        UUID id = event.getId();

        if(acolhidaMembers == null) acolhidaMembers = new HashSet<>();

        return new Missa(id, event, comentariosMember, leitura1Member, salmoMember, leitura2Member, precesMember, acolhidaMembers);
    }

    public void removeComentariosMember(){
        this.comentariosMember = null;
    }

    public void removeLeitura1Member(){
        this.leitura1Member = null;
    }

    public void removeSalmoMember(){
        this.salmoMember = null;
    }

    public void removeLeitura2Member(){
        this.leitura2Member = null;
    }

    public void removePrecesMember(){
        this.precesMember = null;
    }

    public void setComentariosMember(Member comentariosMember) {
        this.comentariosMember = comentariosMember;
    }

    public void setLeitura1Member(Member leitura1Member) {
        this.leitura1Member = leitura1Member;
    }

    public void setSalmoMember(Member salmoMember) {
        this.salmoMember = salmoMember;
    }

    public void setLeitura2Member(Member leitura2Member) {
        this.leitura2Member = leitura2Member;
    }

    public void setPrecesMember(Member precesMember) {
        this.precesMember = precesMember;
    }

    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Member getComentariosMember() {
        return comentariosMember;
    }

    public Member getLeitura1Member() {
        return leitura1Member;
    }

    public Member getSalmoMember() {
        return salmoMember;
    }

    public Member getLeitura2Member() {
        return leitura2Member;
    }

    public Member getPrecesMember() {
        return precesMember;
    }

    public Set<Member> getAcolhidaMembers() {
        return Collections.unmodifiableSet(acolhidaMembers);
    }
}
