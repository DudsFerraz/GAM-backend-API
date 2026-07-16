package br.org.gam.api.account.application;

import br.org.gam.api.account.application.useCases.getCurrentAccountContext.CurrentAccountContextRDTO;
import br.org.gam.api.rbac.accountRole.application.AccountRolesRDTO;
import br.org.gam.api.shared.domain.GamEmail;
import br.org.gam.api.testing.annotation.StructuralTest;
import br.org.gam.api.testing.annotation.UnitTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@StructuralTest
@DisplayName("Account Record Serialization")
class AccountRecordSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("empty role wrapper -> empty roles array")
    void emptyRoleWrapperShouldSerializeAsEmptyRolesArray() throws Exception {
        JsonNode response = serialize(new AccountRolesRDTO());

        assertThat(response.path("roles").isArray()).isTrue();
        assertThat(response.path("roles")).isEmpty();
    }

    @Test
    @DisplayName("null role wrapper -> empty roles array")
    void nullRoleWrapperShouldSerializeAsEmptyRolesArray() throws Exception {
        JsonNode response = serialize(null);

        assertThat(response.path("roles").isArray()).isTrue();
        assertThat(response.path("roles")).isEmpty();
    }

    @Test
    @DisplayName("REQ-ACCOUNT-008 - null current-context collections -> exact response with empty arrays")
    void nullCurrentContextCollectionsShouldSerializeAsEmptyArrays() throws Exception {
        CurrentAccountContextRDTO currentContext = new CurrentAccountContextRDTO(
                UUID.randomUUID(),
                GamEmail.of("current-context@example.com"),
                "Current Context",
                null,
                null
        );

        JsonNode response = objectMapper.readTree(objectMapper.writeValueAsString(currentContext));

        assertThat(response.size()).isEqualTo(5);
        assertThat(response.has("id")).isTrue();
        assertThat(response.has("email")).isTrue();
        assertThat(response.has("displayName")).isTrue();
        assertThat(response.path("roles").isArray()).isTrue();
        assertThat(response.path("roles")).isEmpty();
        assertThat(response.path("permissions").isArray()).isTrue();
        assertThat(response.path("permissions")).isEmpty();
    }

    private JsonNode serialize(AccountRolesRDTO roles) throws Exception {
        AccountRDTO response = new AccountRDTO(
                UUID.randomUUID(),
                GamEmail.of("record@example.com"),
                "Record",
                roles
        );
        return objectMapper.readTree(objectMapper.writeValueAsString(response));
    }
}
