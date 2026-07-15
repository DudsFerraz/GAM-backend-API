package br.org.gam.api.shared.exception;

import br.org.gam.api.testing.annotation.FunctionalTest;
import br.org.gam.api.testing.annotation.UnitTest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@UnitTest
@FunctionalTest
@DisplayName("API error envelope")
class ApiErrorDTOContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("REQ-OPENAPI-006 and REQ-OPENAPI-008 - serialized error -> five-field UTC diagnostic envelope")
    void serializedErrorShouldExposeOnlyTheCommonFiveFieldEnvelope() {
        ApiErrorDTO error = new ApiErrorDTO(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Member not found with the supplied identifier.",
                Map.of("resource", "Member", "identifier", "0190d5d4-52b3-7d30-a8d3-64b70d6c3142")
        );
        Map<String, Object> payload = objectMapper.convertValue(error, new TypeReference<>() { });

        assertThat(payload).containsOnlyKeys("timestamp", "status", "code", "message", "details");
        assertThat(payload.get("timestamp").toString()).endsWith("Z");
        assertThat(payload).containsEntry("status", 404)
                .containsEntry("code", "RESOURCE_NOT_FOUND")
                .containsEntry("message", "Member not found with the supplied identifier.");
        assertThat(payload.get("details"))
                .isEqualTo(Map.of("resource", "Member", "identifier", "0190d5d4-52b3-7d30-a8d3-64b70d6c3142"));
    }
}
