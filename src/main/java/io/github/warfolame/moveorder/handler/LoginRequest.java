package io.github.warfolame.moveorder.handler;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * JSON body of {@code POST /api/v1/auth/login}: the only request that carries a password.
 * The validation stays here, at the HTTP edge.
 */
public record LoginRequest(
        @NotBlank
        @Schema(example = "aliya")
        String username,

        @NotBlank
        @Schema(example = "dispatcher-dev", format = "password")
        String password) {

    /** A record would print the password; this one never does. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
