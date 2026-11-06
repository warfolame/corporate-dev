package io.github.warfolame.moveorder.domain;

/**
 * No move order has this id. Unchecked, like {@link DuplicateMoveOrder}.
 */
public class MoveOrderNotFound extends RuntimeException {

    private final MoveOrderId id;

    public MoveOrderNotFound(MoveOrderId id) {
        super("move order not found: " + id.value());
        this.id = id;
    }

    public MoveOrderId id() {
        return id;
    }
}
