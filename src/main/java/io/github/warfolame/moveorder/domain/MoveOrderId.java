package io.github.warfolame.moveorder.domain;

/**
 * Identifier of a move order, e.g. "MO-2026-0042".
 *
 * @param value the identifier text; must not be null or blank
 */
public record MoveOrderId(String value) {

    public MoveOrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MoveOrderId must not be null or blank");
        }
    }
}
