package io.github.warfolame.moveorder.config;

import io.github.warfolame.moveorder.domain.DuplicateMoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderId;
import io.github.warfolame.moveorder.domain.MoveOrderNotFound;
import io.github.warfolame.moveorder.domain.MoveOrderNumber;
import io.github.warfolame.moveorder.domain.MoveOrderRule;
import io.github.warfolame.moveorder.domain.MoveOrderStatus;
import io.github.warfolame.moveorder.domain.TransitionNotAllowed;
import io.github.warfolame.moveorder.persistence.MoveOrderJdbc;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers move orders and changes their status. All business rules come in
 * through {@link MoveOrderRule}; storage goes through {@link MoveOrderJdbc}.
 * Who may call {@link #complete} is checked here too, not only on the URL.
 */
@Service
public class MoveOrderService {

    private static final Logger log = LoggerFactory.getLogger(MoveOrderService.class);

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
     * Reads one stored order. Changes nothing.
     *
     * @throws MoveOrderNotFound if no order has this id
     */
    public MoveOrder get(MoveOrderId id) {
        return orders.findById(id).orElseThrow(() -> new MoveOrderNotFound(id));
    }

    /**
     * The locked action: the move is done, the customer is billed and the crew is paid. Only a
     * manager may do it. {@code @PreAuthorize} is the second lock behind the URL rule in
     * {@link SecurityConfig}, with the same role: for a dispatcher this method never starts,
     * so no row is read and none changes. The domain rules still decide whether the move is allowed.
     *
     * @param actor who completes it: {@code Authentication.getName()}, the token's {@code sub},
     *              never a name from the request body
     * @return the order in status COMPLETED
     * @throws MoveOrderNotFound    if no order has this id
     * @throws TransitionNotAllowed if the status table forbids it (only IN_PROGRESS may complete); nothing is written
     */
    @Transactional
    @PreAuthorize("hasRole('" + Roles.MANAGER + "')")
    public MoveOrder complete(MoveOrderId id, String actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        MoveOrder order = orders.findByIdForUpdate(id).orElseThrow(() -> new MoveOrderNotFound(id));
        MoveOrder completed = move(order, MoveOrderStatus.COMPLETED);
        orders.updateStatus(completed);
        log.info("move order {} ({}) {} -> {} by {}",
                order.number().value(), id.value(), order.status(), completed.status(), actor);
        return completed;
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
