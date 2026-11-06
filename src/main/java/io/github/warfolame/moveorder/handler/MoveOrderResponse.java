package io.github.warfolame.moveorder.handler;

import io.github.warfolame.moveorder.domain.MoveOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * JSON body of a move order on the wire. Built from the domain {@link MoveOrder},
 * so the table shape and the domain record never reach Jackson.
 *
 * @param status the same token the {@code CHECK} constraint stores, e.g. {@code REQUESTED}
 */
public record MoveOrderResponse(
        UUID id,
        @Schema(example = "MO-2026-0042") String businessKey,
        @Schema(example = "REQUESTED") String status,
        @Schema(example = "2-room flat: Abay 10 -> Dostyk 5") String title) {

    static MoveOrderResponse from(MoveOrder order) {
        return new MoveOrderResponse(
                order.id().value(),
                order.number().value(),
                order.status().name(),
                order.title());
    }
}
