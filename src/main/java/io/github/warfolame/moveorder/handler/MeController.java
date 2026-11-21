package io.github.warfolame.moveorder.handler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/me}: the {@link Authentication} the filter put into the security context for
 * this request. Nothing comes from the request body; the page shows the role from here.
 */
@RestController
@Tag(name = "Auth")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MeController {

    @GetMapping("/api/me")
    @Operation(summary = "Who am I: the username and the authorities from my token")
    @ApiResponse(responseCode = "200", description = "username and authorities",
            content = @Content(schema = @Schema(implementation = MeResponse.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: valid token required",
            content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    public MeResponse me(Authentication authentication) {
        return new MeResponse(
                authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .sorted()
                        .toList());
    }
}
