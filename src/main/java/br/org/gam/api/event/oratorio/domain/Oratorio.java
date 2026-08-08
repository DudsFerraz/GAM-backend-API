package br.org.gam.api.event.oratorio.domain;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.member.domain.Member;
import br.org.gam.api.oratoriano.domain.Oratoriano;
import java.util.*;

public class Oratorio {
    private UUID id;
    private Event event;
    private String cancellationReason;
    private Set<Member> lancheMembers;
    private Set<Member> btJovensMembers;
    private Set<Member> btCriancasMembers;
    private Set<Oratoriano> oratorianos;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO (JPA/MapStruct).</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(Event event, Set lancheMembers, Set btJovensMembers, Set btCriancasMembers, Set oratorianos)}.
     */
    @Deprecated
    public Oratorio(UUID id, Event event, String cancellationReason, Set<Member> lancheMembers, Set<Member> btJovensMembers, Set<Member> btCriancasMembers, Set<Oratoriano> oratorianos) {
        this.id = id;
        this.event = event;
        this.cancellationReason = cancellationReason;
        this.lancheMembers = lancheMembers != null ? lancheMembers : new HashSet<>();
        this.btJovensMembers = btJovensMembers != null ? btJovensMembers : new HashSet<>();
        this.btCriancasMembers = btCriancasMembers != null ? btCriancasMembers : new HashSet<>();
        this.oratorianos = oratorianos != null ? oratorianos : new HashSet<>();
    }

    public static Oratorio register(Event event, Set<Member> lancheMembers, Set<Member> btJovensMembers, Set<Member> btCriancasMembers, Set<Oratoriano> oratorianos){
        Objects.requireNonNull(event, "event cannot be null");

        UUID id = event.getId();

        if(lancheMembers == null) lancheMembers = new HashSet<>();
        if(btJovensMembers == null) btJovensMembers = new HashSet<>();
        if(btCriancasMembers == null) btCriancasMembers = new HashSet<>();
        if(oratorianos == null) oratorianos = new HashSet<>();

        return new Oratorio(id,event, null, lancheMembers,  btJovensMembers, btCriancasMembers, oratorianos);
    }

    public void addLancheMember(Member member){
        this.lancheMembers.add(member);
    }

    public void removeLancheMember(Member member){
        this.lancheMembers.remove(member);
    }

    public void addBtJovensMember(Member member){
        this.btJovensMembers.add(member);
    }

    public void  removeBtJovensMember(Member member){
        this.btJovensMembers.remove(member);
    }

    public void addBtCriancasMember(Member member){
        this.btCriancasMembers.add(member);
    }

    public void removeBtCriancasMember(Member member){
        this.btCriancasMembers.remove(member);
    }

    public void addOratoriano(Oratoriano oratoriano){
        this.oratorianos.add(oratoriano);
    }

    public void removeOratoriano(Oratoriano oratoriano){
        this.oratorianos.remove(oratoriano);
    }

    public UUID getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Set<Member> getLancheMembers() {
        return Collections.unmodifiableSet(lancheMembers);
    }

    public Set<Member> getBtJovensMembers() {
        return Collections.unmodifiableSet(btJovensMembers);
    }

    public Set<Member> getBtCriancasMembers() {
        return Collections.unmodifiableSet(btCriancasMembers);
    }

    public Set<Oratoriano> getOratorianos() {
        return Collections.unmodifiableSet(oratorianos);
    }
}
