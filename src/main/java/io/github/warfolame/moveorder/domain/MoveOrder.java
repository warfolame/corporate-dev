package io.github.warfolame.moveorder.domain;

import java.util.Objects;

/**
 * A move order: who it is ({@code id}, {@code number}), what the move is ({@code title}),
 * where it is in its lifecycle ({@code status}) and whether the customer has paid the deposit.
 */
public record MoveOrder(
        MoveOrderId id,
        MoveOrderNumber number,
        String title,
        MoveOrderStatus status,
        boolean depositPaid) {

    public MoveOrder {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(number, "number must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be null or blank");
        }
    }

    /** A new order: just requested, deposit not paid yet. */
    public static MoveOrder requested(MoveOrderId id, MoveOrderNumber number, String title) {
        return new MoveOrder(id, number, title, MoveOrderStatus.REQUESTED, false);
    }

    /** The same order after the customer has paid the deposit. */
    public MoveOrder payDeposit() {
        return new MoveOrder(id, number, title, status, true);
    }

    /** The same order in another status. Rules are checked by the caller, not here. */
    public MoveOrder withStatus(MoveOrderStatus newStatus) {
        return new MoveOrder(id, number, title, newStatus, depositPaid);
    }
}
