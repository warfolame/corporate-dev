package io.github.warfolame.moveorder.domain;

import static io.github.warfolame.moveorder.domain.MoveOrderStatus.COMPLETED;
import static io.github.warfolame.moveorder.domain.MoveOrderStatus.IN_PROGRESS;
import static io.github.warfolame.moveorder.domain.MoveOrderStatus.SCHEDULED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class MoveOrderRuleTest {

    private final MoveOrder unpaid = MoveOrder.requested(new MoveOrderId("MO-2026-0042"));
    private final MoveOrder paid = unpaid.payDeposit();

    private final MoveOrderRule transitionRule = new TransitionRule(new MoveOrderPolicy());
    private final MoveOrderRule depositRule = new DepositRequiredRule();
    private final MoveOrderRule chain = new MoveOrderRuleChain(List.of(transitionRule, depositRule));

    @Test
    void transitionRuleAllowsTableTransition() {
        assertDoesNotThrow(() -> transitionRule.check(unpaid, SCHEDULED));
    }

    @Test
    void transitionRuleForbidsSkippingScheduling() {
        assertThrows(IllegalStateException.class, () -> transitionRule.check(paid, COMPLETED));
    }

    @Test
    void depositRuleBlocksSchedulingWithoutDeposit() {
        assertThrows(IllegalStateException.class, () -> depositRule.check(unpaid, SCHEDULED));
    }

    @Test
    void depositRuleAllowsSchedulingWithDeposit() {
        assertDoesNotThrow(() -> depositRule.check(paid, SCHEDULED));
    }

    @Test
    void depositRuleIgnoresOtherTransitions() {
        assertDoesNotThrow(() -> depositRule.check(unpaid.withStatus(SCHEDULED), IN_PROGRESS));
    }

    @Test
    void chainBlocksTableTransitionWhenDepositIsUnpaid() {
        assertThrows(IllegalStateException.class, () -> chain.check(unpaid, SCHEDULED));
    }

    @Test
    void chainAllowsTableTransitionWhenDepositIsPaid() {
        assertDoesNotThrow(() -> chain.check(paid, SCHEDULED));
    }

    @Test
    void chainBlocksForbiddenTransitionEvenWithDeposit() {
        assertThrows(IllegalStateException.class, () -> chain.check(paid, COMPLETED));
    }
}
