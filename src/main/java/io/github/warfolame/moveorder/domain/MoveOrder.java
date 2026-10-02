package io.github.warfolame.moveorder.domain;

import java.util.Objects;

/**
 * A move order: who it is ({@code id}), where it is in its lifecycle ({@code status})
 * and whether the customer has paid the deposit.
 */
public record MoveOrder(MoveOrderId id, MoveOrderStatus status, boolean depositPaid) {

    public MoveOrder {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    /** A new order: just requested, deposit not paid yet. */
    public static MoveOrder requested(MoveOrderId id) {
        return new MoveOrder(id, MoveOrderStatus.REQUESTED, false);
    }

    /** The same order after the customer has paid the deposit. */
    public MoveOrder payDeposit() {
        return new MoveOrder(id, status, true);
    }

    /** The same order in another status. Rules are checked by the caller, not here. */
    public MoveOrder withStatus(MoveOrderStatus newStatus) {
        return new MoveOrder(id, newStatus, depositPaid);
    }
}
