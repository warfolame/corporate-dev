package io.github.warfolame.moveorder.domain;

/**
 * An order with this number already exists. A retry or a double click is not a second order.
 * Unchecked on purpose: leaving a transactional method with it rolls the transaction back.
 */
public class DuplicateMoveOrder extends RuntimeException {

    private final MoveOrderNumber number;

    public DuplicateMoveOrder(MoveOrderNumber number, Throwable cause) {
        super("duplicate move order: " + number.value(), cause);
        this.number = number;
    }

    public MoveOrderNumber number() {
        return number;
    }
}
