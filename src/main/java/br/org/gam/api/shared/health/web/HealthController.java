package br.org.gam.api.shared.health.web;

import br.org.gam.api.shared.health.application.GetHealth;
import br.org.gam.api.shared.health.application.HealthRDTO;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final GetHealth getHealth;

    public HealthController(GetHealth getHealth) {
        this.getHealth = getHealth;
    }

    @Operation(
            operationId = "getProductionHealth",
            summary = "Check production readiness",
            description = "Returns only the dependency-aware production readiness status. "
                    + "The response contains no infrastructure or diagnostic details.",
            security = {}
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "The application and required database connectivity are ready.",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "Prevents storage of the readiness response.",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = HealthRDTO.class),
                            examples = {
                                    @ExampleObject(
                                            name = "ready",
                                            summary = "Ready",
                                            value = "{\"status\":\"UP\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "The application is reachable but required database connectivity is unavailable.",
                    headers = @Header(
                            name = HttpHeaders.CACHE_CONTROL,
                            description = "Prevents storage of the readiness response.",
                            schema = @Schema(type = "string", example = "no-store")
                    ),
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = HealthRDTO.class),
                            examples = {
                                    @ExampleObject(
                                            name = "unavailable",
                                            summary = "Required dependency unavailable",
                                            value = "{\"status\":\"DOWN\"}"
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "405",
                    description = "Only GET is supported on the public health path."
            )
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<HealthRDTO> get() {
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
