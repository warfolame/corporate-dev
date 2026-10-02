package io.github.warfolame.moveorder.domain;

import java.util.Objects;

/**
 * The status table: only transitions allowed by {@link MoveOrderPolicy} may happen.
 */
public final class TransitionRule implements MoveOrderRule {

    private final MoveOrderPolicy policy;

    public TransitionRule(MoveOrderPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    @Override
    public void check(MoveOrder order, MoveOrderStatus to) {
        policy.move(order.status(), to);
    }
}
