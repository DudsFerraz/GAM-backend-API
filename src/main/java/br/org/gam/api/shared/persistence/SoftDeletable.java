package br.org.gam.api.shared.persistence;

import java.time.Instant;
import java.util.UUID;

public interface SoftDeletable {
    void setDeletedAt(Instant deletedAt);
    void setDeletedBy(UUID deletedBy);
}
