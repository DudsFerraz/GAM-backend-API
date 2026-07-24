package br.org.gam.persistencecontract;

import br.org.gam.api.shared.domain.GamRG;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity(name = "GamRgPersistenceContractFixture")
@Table(name = "gam_rg_persistence_contracts")
public class GamRgPersistenceContractFixture {

    @Id
    private UUID id;

    private GamRG rg;

    protected GamRgPersistenceContractFixture() {
    }

    public GamRgPersistenceContractFixture(UUID id, GamRG rg) {
        this.id = id;
        this.rg = rg;
    }

    public GamRG rg() {
        return rg;
    }
}
