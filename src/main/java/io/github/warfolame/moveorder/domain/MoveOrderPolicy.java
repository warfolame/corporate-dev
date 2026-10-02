package io.github.warfolame.moveorder.domain;

import static io.github.warfolame.moveorder.domain.MoveOrderStatus.COMPLETED;
import static io.github.warfolame.moveorder.domain.MoveOrderStatus.IN_PROGRESS;
import static io.github.warfolame.moveorder.domain.MoveOrderStatus.REQUESTED;
import static io.github.warfolame.moveorder.domain.MoveOrderStatus.SCHEDULED;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Decides which status changes of a move order are allowed.
 */
public final class MoveOrderPolicy {

    /** For each status: the statuses an order may move to next. Anything else is forbidden. */
    private static final Map<MoveOrderStatus, Set<MoveOrderStatus>> ALLOWED = Map.of(
            REQUESTED, Set.of(SCHEDULED),
            SCHEDULED, Set.of(IN_PROGRESS),
            IN_PROGRESS, Set.of(COMPLETED),
            COMPLETED, Set.of());

    /**
     * Moves an order from one status to another.
     *
     * @return the new status {@code to} if the transition is allowed
     * @throws IllegalStateException if the transition is forbidden
     */
    public MoveOrderStatus move(MoveOrderStatus from, MoveOrderStatus to) {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");

        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("Move order cannot go from " + from + " to " + to);
        }
        return to;
    }
}
