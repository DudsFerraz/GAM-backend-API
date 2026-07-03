package br.org.gam.api.event.domain;

import br.org.gam.api.shared.persistence.UUIDGenerator;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Event {
    private UUID id;
    private String title;
    private String description;
    private EventType type;
    private EventStatus status;
    private String cancellationReason;
    private Instant beginDate;
    private Instant endDate;

    /**
     * @deprecated Constructor for internal mapper usage. Prefer {@link #register(String, String, Instant, Instant, EventType)}.
     */
    @Deprecated
    public Event(UUID id, String title, String description, Instant beginDate, Instant endDate, EventType type,
                 EventStatus status, String cancellationReason) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.beginDate = beginDate;
        this.endDate = endDate;
        this.type = type;
        this.status = status;
        this.cancellationReason = cancellationReason;
    }

    public static Event register(String title, String description, Instant beginDate, Instant endDate, EventType type) {
        Objects.requireNonNull(title, "Title cannot be null");
        Objects.requireNonNull(beginDate, "Begin date cannot be null");
        Objects.requireNonNull(endDate, "End date cannot be null");
        Objects.requireNonNull(type, "Event type cannot be null");
        if (!endDate.isAfter(beginDate)) throw new IllegalArgumentException("endDate must be after beginDate.");

        EventStatus status = EventStatus.SCHEDULED;
        if (endDate.isBefore(Instant.now())) status = EventStatus.COMPLETED;

        title = title.trim();

        if (description == null) description = "";
        description = description.trim();

        UUID id = UUIDGenerator.generateUUIDV7();

        return new Event(id, title, description, beginDate, endDate, type, status, null);
    }

    public void cancel(String reason) {
        Objects.requireNonNull(reason, "Cancellation reason cannot be null");
        reason = reason.trim();
        if (reason.isBlank()) throw new IllegalArgumentException("Cancellation reason cannot be blank.");
        this.status = EventStatus.CANCELLED;
        this.cancellationReason = reason;
    }

    public void lock() {
        if (this.status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled events cannot be locked.");
        }
        this.status = EventStatus.LOCKED;
    }

    public void finalizeEvent() {
        if (this.status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled events cannot be finalized.");
        }
        this.status = EventStatus.FINALIZED;
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

    public Instant getBeginDate() {
        return beginDate;
    }

    public Instant getEndDate() {
        return endDate;
    }

    public EventType getType() {
        return type;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }
}
