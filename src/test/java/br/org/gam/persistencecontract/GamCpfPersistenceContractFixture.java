package br.org.gam.persistencecontract;

import br.org.gam.api.shared.domain.GamCPF;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity(name = "GamCpfPersistenceContractFixture")
@Table(name = "gam_cpf_persistence_contracts")
public class GamCpfPersistenceContractFixture {

    @Id
    private UUID id;

    private GamCPF cpf;

    protected GamCpfPersistenceContractFixture() {
    }

    public GamCpfPersistenceContractFixture(UUID id, GamCPF cpf) {
        this.id = id;
        this.cpf = cpf;
    }

    public GamCPF cpf() {
        return cpf;
    }
}
