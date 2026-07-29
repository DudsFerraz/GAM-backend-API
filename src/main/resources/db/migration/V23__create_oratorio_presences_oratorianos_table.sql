CREATE TABLE oratorio_presences_oratorianos (
    oratoriano_id UUID NOT NULL,
    oratorio_id UUID NOT NULL,

    PRIMARY KEY (oratorio_id, oratoriano_id),
    CONSTRAINT fk_presences_oratoriano
        FOREIGN KEY (oratoriano_id) REFERENCES oratorianos(id),
    CONSTRAINT fk_presences_oratorio
        FOREIGN KEY (oratorio_id) REFERENCES oratorios(id)
);
