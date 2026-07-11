package br.org.gam.api.api;

import br.org.gam.api.event.domain.Event;
import br.org.gam.api.rbac.permission.domain.PermissionEnum;
import br.org.gam.api.testing.annotation.ApiTest;
import br.org.gam.api.testing.annotation.IntegrationTest;
import br.org.gam.api.testing.annotation.SecurityTest;
import br.org.gam.api.testing.integration.BaseApiIntegrationTest;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@ApiTest
@IntegrationTest
@SecurityTest
@DisplayName("API - Resource Security")
class ResourceSecurityApiIT extends BaseApiIntegrationTest {

    @Test
    @DisplayName("protected endpoint without token -> HTTP 401")
    void protectedEndpointWithoutTokenShouldReturnUnauthorized() {
        jsonRequest()
                .get("/accounts/{id}", UUID.randomUUID())
                .then()
                .statusCode(401)
                .body("status", equalTo(401))
                .body("message", containsString("Authentication failed"));
    }

    @Test
    @DisplayName("malformed bearer token -> HTTP 401")
    void malformedBearerTokenShouldReturnUnauthorized() {
        jsonRequest()
                .header("Authorization", "Bearer not-a-jwt")
                .get("/accounts/{id}", UUID.randomUUID())
                .then()
                .statusCode(401)
                .body("status", equalTo(401))
                .body("message", containsString("Authentication failed"));
    }

    @Test
    @DisplayName("bearer token for deleted Account -> HTTP 401")
    void bearerTokenForDeletedAccountShouldReturnUnauthorized() {
        AuthSession session = registerAndLogin(null);
        softDeleteAccount(session.accountId());

        jsonRequest()
                .header("Authorization", "Bearer " + session.accessToken())
                .get("/accounts/{id}", session.accountId())
                .then()
                .statusCode(401)
                .body("status", equalTo(401))
                .body("message", containsString("Authentication failed"));
    }

    @Test
    @DisplayName("missing permission -> HTTP 403")
    void missingPermissionShouldReturnForbidden() {
        AuthSession member = registerAndLogin("MEMBER");
        UUID locationId = createLocation(member, "Forbidden Event Location");
        UUID requiredPermissionId = permissionId(PermissionEnum.EVENT_GET_S);

        authenticatedJsonRequest(member)
                .body(eventPayload("Forbidden Event", locationId, requiredPermissionId))
                .post("/events")
                .then()
                .statusCode(403)
                .body("status", equalTo(403))
                .body("message", containsString("Access denied"));
    }

    @Test
    @DisplayName("not found resource -> HTTP 404")
    void notFoundResourceShouldReturnNotFound() {
        AuthSession coord = registerAndLogin("COORD");

        authenticatedJsonRequest(coord)
                .get("/accounts/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("status", equalTo(404))
                .body("error", equalTo("Not Found"));
    }

    @Test
    @DisplayName("valid location request -> HTTP 201, Location header, and persisted row")
    void validLocationRequestShouldReturnCreatedPayloadAndPersistRow() {
        AuthSession member = registerAndLogin("MEMBER");

        ExtractableResponse<Response> response = authenticatedJsonRequest(member)
                .body(locationPayload("API Location"))
                .post("/locations")
                .then()
                .statusCode(201)
                .header("Location", containsString("/locations/"))
                .body("id", notNullValue())
                .extract();

        UUID locationId = UUID.fromString(response.path("id"));
        trackLocation(locationId);
        assertThat(locationExists(locationId)).isTrue();
    }

    @Test
    @DisplayName("valid event request with permission -> HTTP 201 and persisted row")
    void validEventRequestWithPermissionShouldReturnCreatedPayloadAndPersistRow() {
        AuthSession coord = registerAndLogin("COORD");
        UUID locationId = createLocation(coord, "API Event Location");
        UUID requiredPermissionId = permissionId(PermissionEnum.EVENT_GET_S);

        ExtractableResponse<Response> response = authenticatedJsonRequest(coord)
                .body(eventPayload("API Event", locationId, requiredPermissionId))
                .post("/events")
                .then()
                .statusCode(201)
                .header("Location", containsString("/events/"))
                .body("id", notNullValue())
                .extract();

        UUID eventId = UUID.fromString(response.path("id"));
        trackEvent(eventId);
        assertThat(eventExists(eventId)).isTrue();
    }

    @Test
    @DisplayName("invalid event payload -> HTTP 400")
    void invalidEventPayloadShouldReturnBadRequest() {
        AuthSession coord = registerAndLogin("COORD");
        UUID locationId = createLocation(coord, "Invalid Event Location");
        UUID requiredPermissionId = permissionId(PermissionEnum.EVENT_GET_S);
        Map<String, Object> payload = eventPayload("Invalid Event", locationId, requiredPermissionId);
        payload = new java.util.HashMap<>(payload);
        payload.put("title", "");

        authenticatedJsonRequest(coord)
                .body(payload)
                .post("/events")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("message", containsString("Validation error"));
    }
}
