package io.github.warfolame.moveorder.handler;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSON body of {@code POST /api/v1/move-orders}. The validation rules live here,
 * at the HTTP edge; {@code domain} has no {@code jakarta.validation}.
 */
public record CreateMoveOrderRequest(
        @NotBlank
        @Schema(example = "MO-2026-0042")
        String businessKey,

        @NotBlank
        @Size(max = 120)
        @Schema(example = "2-room flat: Abay 10 -> Dostyk 5")
        String title) {
}
