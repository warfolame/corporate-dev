package io.github.warfolame.moveorder.config;

import static io.github.warfolame.moveorder.domain.MoveOrderStatus.COMPLETED;
import static io.github.warfolame.moveorder.domain.MoveOrderStatus.SCHEDULED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Starts the Spring context and checks that the service got both rules. */
@SpringBootTest
class MoveOrderServiceTest {

    @Autowired
    private MoveOrderService service;

    private final MoveOrder unpaid = MoveOrder.requested(new MoveOrderId("MO-2026-0042"));

    @Test
    void movesOrderWhenAllRulesPass() {
        assertEquals(SCHEDULED, service.move(unpaid.payDeposit(), SCHEDULED).status());
    }

    @Test
    void depositStopFactorIsWiredIn() {
        assertThrows(IllegalStateException.class, () -> service.move(unpaid, SCHEDULED));
    }

    @Test
    void statusTableIsWiredIn() {
        assertThrows(IllegalStateException.class, () -> service.move(unpaid.payDeposit(), COMPLETED));
    }
}
