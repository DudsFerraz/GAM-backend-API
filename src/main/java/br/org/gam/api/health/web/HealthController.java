package br.org.gam.api.health.web;

import br.org.gam.api.shared.health.application.GetHealth;
import br.org.gam.api.shared.health.application.HealthRDTO;
import br.org.gam.api.health.application.HealthReadiness;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health")
public class HealthController {

    private final GetHealth getHealth;

    public HealthController(ObjectProvider<GetHealth> getHealth, HealthReadiness healthReadiness) {
        this.getHealth = getHealth.getIfAvailable(() -> new GetHealth(healthReadiness));
    }

    @Operation(
            operationId = "getProductionHealth",
            security = {},
            summary = "Check production readiness",
            description = "Returns only the dependency-aware production readiness status. "
                    + "The response contains no infrastructure or diagnostic details."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The application and required database connectivity are ready.",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "Prevents storage of the public readiness response.",
                            required = true,
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    type = "object",
                                    requiredProperties = {"status"},
                                    example = "{\"status\":\"UP\"}"
                            ),
                            examples = @ExampleObject(
                                    name = "ready",
                                    summary = "Ready",
                                    value = "{\"status\":\"UP\"}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "The application is reachable but required database connectivity is unavailable.",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "Prevents storage of the public readiness response.",
                            required = true,
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    type = "object",
                                    requiredProperties = {"status"},
                                    example = "{\"status\":\"DOWN\"}"
                            ),
                            examples = @ExampleObject(
                                    name = "unavailable",
                                    summary = "Required dependency unavailable",
                                    value = "{\"status\":\"DOWN\"}"
                            )
                    )
            )
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HealthRDTO> getHealth() {
        HealthRDTO health = getHealth.get();
        HttpStatus status = "UP".equals(health.status())
                ? HttpStatus.OK
                : HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(health);
    }

    @Hidden
    @RequestMapping(method = {
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE,
            RequestMethod.HEAD,
            RequestMethod.OPTIONS
    })
    public ResponseEntity<Void> methodNotAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }
}
