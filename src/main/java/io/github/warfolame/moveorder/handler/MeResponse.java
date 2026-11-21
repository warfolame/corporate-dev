package io.github.warfolame.moveorder.handler;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Who this token belongs to, as the filter chain understood it.
 *
 * @param username    {@code sub} of the token
 * @param authorities the roles from the token's {@code roles} claim, with the {@code ROLE_} prefix
 */
public record MeResponse(
        @Schema(example = "aliya") String username,
        @Schema(example = "[\"ROLE_DISPATCHER\"]") List<String> authorities) {
}
