package br.org.gam.api.event.domain;

import br.org.gam.api.location.domain.Location;
import br.org.gam.api.rbac.Permission.domain.Permission;
import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Event {
    private UUID id;
    private String title;
    private String description;
    private Location location;
    private Permission requiredPermission;
    private EventType type;
    private EventStatus status;
    private Instant beginDate;
    private Instant endDate;

    /**
     * @deprecated <b>ESTE CONSTRUTOR É EXCLUSIVO PARA USO INTERNO E JPA/MapStruct.</b>
     * <br> <br>
     * <b> Use o método fábrica {@link #register(String title, String description, Location location, Permission requiredPermission, Instant beginDate, Instant endDate, EventType type)}.
     */
    @Deprecated
    public Event(UUID id, String title, String description, Location location, Permission requiredPermission, Instant beginDate, Instant endDate, EventType type, EventStatus status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.location = location;
        this.requiredPermission = requiredPermission;
        this.beginDate = beginDate;
        this.endDate = endDate;
        this.type = type;
        this.status = status;
    }

    public static Event register(String title, String description, Location location, Permission requiredPermission, Instant beginDate, Instant endDate, EventType type) {
        Objects.requireNonNull(title, "Title cannot be null");
        Objects.requireNonNull(beginDate, "Begin date cannot be null");
        Objects.requireNonNull(endDate, "End date cannot be null");
        Objects.requireNonNull(type, "Event type cannot be null");
        if (!endDate.isAfter(beginDate)) throw new IllegalArgumentException("endDate must be after beginDate.");

        EventStatus status = EventStatus.SCHEDULED;
        if(endDate.isBefore(Instant.now())) status = EventStatus.COMPLETED;

        title = title.trim();

        if (description == null) description = "";
        description = description.trim();

        UUID id = UUIDGenerator.generateUUIDV7();

        return new Event(id, title, description, location, requiredPermission ,beginDate, endDate, type, status);
    }

    public void cancel(){
        this.status = EventStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Location getLocation() {
        return location;
    }

    public Instant getBeginDate() {
        return beginDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public Permission getRequiredPermission() {
        return requiredPermission;
    }

    public EventType getType() {
        return type;
    }

    public EventStatus getStatus() {
        return status;
    }
}