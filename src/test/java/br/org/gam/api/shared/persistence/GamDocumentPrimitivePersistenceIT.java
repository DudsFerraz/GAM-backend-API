package br.org.gam.api.shared.persistence;

import br.org.gam.api.shared.domain.GamCPF;
import br.org.gam.api.shared.domain.GamRG;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.PersistenceTest;
import br.org.gam.api.testing.integration.PostgreSQLIntegrationTest;
import br.org.gam.persistencecontract.GamCpfPersistenceContractFixture;
import br.org.gam.persistencecontract.GamRgPersistenceContractFixture;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@PersistenceTest
@Transactional
@SpringBootTest(classes = GamDocumentPrimitivePersistenceIT.PersistenceContractTestApplication.class)
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/test-fixtures/gam-document-primitives",
        "spring.flyway.schemas=gam_document_persistence_contract",
        "spring.flyway.default-schema=gam_document_persistence_contract",
        "spring.jpa.properties.hibernate.default_schema=gam_document_persistence_contract"
})
@EntityScan(basePackageClasses = {
        GamCPFConverterJPA.class,
        GamRGConverterJPA.class,
        GamCpfPersistenceContractFixture.class,
        GamRgPersistenceContractFixture.class
})
@DisplayName("Persistence - GamCPF and GamRG JPA contracts")
class GamDocumentPrimitivePersistenceIT extends PostgreSQLIntegrationTest {

    private static final String CONTRACT_SCHEMA = "gam_document_persistence_contract";
    private static final String CPF_TABLE = "gam_cpf_persistence_contracts";
    private static final String RG_TABLE = "gam_rg_persistence_contracts";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("GamCPF -> canonical database value and equal rehydrated primitive")
    void gamCpfShouldPersistCanonicallyAndRehydrateEqually() {
        UUID id = UUID.randomUUID();
        GamCPF cpf = new GamCPF(" 529.982.247-25 ");
        entityManager.persist(new GamCpfPersistenceContractFixture(id, cpf));
        entityManager.flush();

        assertThat(databaseValue(CPF_TABLE, id)).isEqualTo("52998224725");

        entityManager.clear();
        GamCpfPersistenceContractFixture rehydrated =
                entityManager.find(GamCpfPersistenceContractFixture.class, id);
        assertThat(rehydrated.cpf()).isEqualTo(cpf);
    }

    @Test
    @DisplayName("null GamCPF -> database null and null rehydrated primitive")
    void nullGamCpfShouldRoundTripAsNull() {
        UUID id = UUID.randomUUID();
        entityManager.persist(new GamCpfPersistenceContractFixture(id, null));
        entityManager.flush();

        assertThat(databaseValue(CPF_TABLE, id)).isNull();

        entityManager.clear();
        GamCpfPersistenceContractFixture rehydrated =
                entityManager.find(GamCpfPersistenceContractFixture.class, id);
        assertThat(rehydrated.cpf()).isNull();
    }

    @Test
    @DisplayName("invalid persisted CPF -> rehydration validation error")
    void invalidPersistedCpfShouldFailRehydration() {
        UUID id = insertDatabaseValue(CPF_TABLE, "52998224724");

        assertThatThrownBy(() -> entityManager.find(GamCpfPersistenceContractFixture.class, id))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("blank persisted CPF -> rehydration validation error")
    void blankPersistedCpfShouldFailRehydration() {
        UUID id = insertDatabaseValue(CPF_TABLE, "   ");

        assertThatThrownBy(() -> entityManager.find(GamCpfPersistenceContractFixture.class, id))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("GamRG -> normalized database value and equal rehydrated primitive")
    void gamRgShouldPersistNormalizedValueAndRehydrateEqually() {
        UUID id = UUID.randomUUID();
        GamRG rg = new GamRG(" 12.345.678-X ");
        entityManager.persist(new GamRgPersistenceContractFixture(id, rg));
        entityManager.flush();

        assertThat(databaseValue(RG_TABLE, id)).isEqualTo("12.345.678-X");

        entityManager.clear();
        GamRgPersistenceContractFixture rehydrated =
                entityManager.find(GamRgPersistenceContractFixture.class, id);
        assertThat(rehydrated.rg()).isEqualTo(rg);
    }

    @Test
    @DisplayName("null GamRG -> database null and null rehydrated primitive")
    void nullGamRgShouldRoundTripAsNull() {
        UUID id = UUID.randomUUID();
        entityManager.persist(new GamRgPersistenceContractFixture(id, null));
        entityManager.flush();

        assertThat(databaseValue(RG_TABLE, id)).isNull();

        entityManager.clear();
        GamRgPersistenceContractFixture rehydrated =
                entityManager.find(GamRgPersistenceContractFixture.class, id);
        assertThat(rehydrated.rg()).isNull();
    }

    @Test
    @DisplayName("blank persisted RG -> rehydration validation error")
    void blankPersistedRgShouldFailRehydration() {
        UUID id = insertDatabaseValue(RG_TABLE, "   ");

        assertThatThrownBy(() -> entityManager.find(GamRgPersistenceContractFixture.class, id))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    private String databaseValue(String table, UUID id) {
        String valueColumn = valueColumn(table);
        return jdbcTemplate.queryForObject(
                "SELECT " + valueColumn + " FROM " + qualifiedTable(table) + " WHERE id = ?",
                String.class,
                id
        );
    }

    private UUID insertDatabaseValue(String table, String value) {
        UUID id = UUID.randomUUID();
        String valueColumn = valueColumn(table);
        jdbcTemplate.update(
                "INSERT INTO " + qualifiedTable(table) + " (id, " + valueColumn + ") VALUES (?, ?)",
                id,
                value
        );
        entityManager.clear();
        return id;
    }

    private String valueColumn(String table) {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND column_name <> 'id'
                """,
                String.class,
                CONTRACT_SCHEMA,
                table
        );

        assertThat(columns)
                .as("%s must map its primitive to exactly one database column", table)
                .hasSize(1);
        return columns.getFirst();
    }

    private String qualifiedTable(String table) {
        return CONTRACT_SCHEMA + "." + table;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class PersistenceContractTestApplication {
    }
}
