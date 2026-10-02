package io.github.warfolame.moveorder.domain;

/**
 * One business rule for changing the status of a move order.
 */
public interface MoveOrderRule {

    /**
     * Checks whether {@code order} may move to status {@code to}.
     *
     * @throws IllegalStateException if the rule forbids the change
     */
    void check(MoveOrder order, MoveOrderStatus to);
}
