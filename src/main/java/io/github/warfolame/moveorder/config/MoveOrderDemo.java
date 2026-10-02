package io.github.warfolame.moveorder.config;

import static io.github.warfolame.moveorder.domain.MoveOrderStatus.COMPLETED;
import static io.github.warfolame.moveorder.domain.MoveOrderStatus.IN_PROGRESS;
import static io.github.warfolame.moveorder.domain.MoveOrderStatus.SCHEDULED;

import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderId;
import io.github.warfolame.moveorder.domain.MoveOrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Runs once on startup and walks one order through its lifecycle, so
 * {@code mvn spring-boot:run} shows the rules working.
 */
@Component
public class MoveOrderDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MoveOrderDemo.class);

    private final MoveOrderService service;

    public MoveOrderDemo(MoveOrderService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        MoveOrder order = MoveOrder.requested(new MoveOrderId("MO-2026-0042"));
        log.info("New order: {}", order);

        tryMove(order, SCHEDULED);

        order = order.payDeposit();
        log.info("Deposit paid");

        order = tryMove(order, SCHEDULED);
        order = tryMove(order, IN_PROGRESS);
        order = tryMove(order, COMPLETED);

        tryMove(order, IN_PROGRESS);
    }

    private MoveOrder tryMove(MoveOrder order, MoveOrderStatus to) {
        try {
            MoveOrder moved = service.move(order, to);
            log.info("{} -> {}: done", order.status(), to);
            return moved;
        } catch (IllegalStateException e) {
            log.info("{} -> {}: blocked ({})", order.status(), to, e.getMessage());
            return order;
        }
    }
}
