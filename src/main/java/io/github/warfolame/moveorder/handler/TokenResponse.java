package io.github.warfolame.moveorder.handler;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JSON body of a successful login. The client keeps {@code accessToken} and sends it as
 * {@code Authorization: Bearer <accessToken>} on every next call. No password in here.
 *
 * @param tokenType always {@code Bearer}
 * @param expiresIn seconds until the token stops working: 900 = 15 minutes
 */
public record TokenResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJtb3ZlLW9yZGVycyIs...") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(example = "900") long expiresIn) {
}
