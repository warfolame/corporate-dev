package io.github.warfolame.moveorder.config;

import io.github.warfolame.moveorder.domain.DuplicateMoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderId;
import io.github.warfolame.moveorder.domain.MoveOrderNumber;
import io.github.warfolame.moveorder.domain.MoveOrderRule;
import io.github.warfolame.moveorder.domain.MoveOrderStatus;
import io.github.warfolame.moveorder.persistence.MoveOrderJdbc;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers move orders and changes their status. All business rules come in
 * through {@link MoveOrderRule}; storage goes through {@link MoveOrderJdbc}.
 */
@Service
public class MoveOrderService {

    private final MoveOrderRule rules;
    private final MoveOrderJdbc orders;

    public MoveOrderService(MoveOrderRule rules, MoveOrderJdbc orders) {
        this.rules = rules;
        this.orders = orders;
    }

    /**
     * @return the order in status {@code to}
     * @throws IllegalStateException if any rule forbids the change
     */
    public MoveOrder move(MoveOrder order, MoveOrderStatus to) {
        rules.check(order, to);
        return order.withStatus(to);
    }

    /**
     * Stores a new order in status REQUESTED. One transaction per call: once this
     * returns, the order is committed.
     *
     * @throws DuplicateMoveOrder if an order with this number already exists; that order stays
     */
    @Transactional
    public MoveOrder register(MoveOrderId id, MoveOrderNumber number, String title) {
        MoveOrder order = MoveOrder.requested(id, number, title);
        orders.insert(order);
        return order;
    }

    /**
     * Proves atomicity: two INSERTs with the same number in one transaction.
     * The second one fails, {@link DuplicateMoveOrder} leaves this method,
     * and the first INSERT is rolled back with it, so nothing is stored.
     *
     * @throws DuplicateMoveOrder always, from the second INSERT
     */
    @Transactional
    public void insertTwice(MoveOrderNumber number) {
        orders.insert(MoveOrder.requested(MoveOrderId.newId(), number, "2-room flat: Abay 10 -> Dostyk 5"));
        orders.insert(MoveOrder.requested(MoveOrderId.newId(), number, "Same flat, sent again"));
    }
}
