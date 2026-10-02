package io.github.warfolame.moveorder.config;

import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderRule;
import io.github.warfolame.moveorder.domain.MoveOrderStatus;
import org.springframework.stereotype.Service;

/**
 * Changes the status of move orders. All business rules come in through {@link MoveOrderRule}.
 */
@Service
public class MoveOrderService {

    private final MoveOrderRule rules;

    public MoveOrderService(MoveOrderRule rules) {
        this.rules = rules;
    }

    /**
     * @return the order in status {@code to}
     * @throws IllegalStateException if any rule forbids the change
     */
    public MoveOrder move(MoveOrder order, MoveOrderStatus to) {
        rules.check(order, to);
        return order.withStatus(to);
    }
}
