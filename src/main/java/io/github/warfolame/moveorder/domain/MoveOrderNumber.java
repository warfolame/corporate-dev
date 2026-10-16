package io.github.warfolame.moveorder.domain;

/**
 * Business number of a move order, e.g. "MO-2026-0042": what the customer sees
 * in the email and says on the phone. One number belongs to at most one order.
 *
 * @param value the number text; must not be null or blank
 */
public record MoveOrderNumber(String value) {

    public MoveOrderNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("MoveOrderNumber must not be null or blank");
        }
    }
}
