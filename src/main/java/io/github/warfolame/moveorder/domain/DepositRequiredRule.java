package io.github.warfolame.moveorder.domain;

/**
 * Stop-factor: no crew is booked for a customer who has not paid the deposit.
 * The status table allows REQUESTED -> SCHEDULED, but this rule still blocks it
 * while the deposit is unpaid.
 */
public final class DepositRequiredRule implements MoveOrderRule {

    @Override
    public void check(MoveOrder order, MoveOrderStatus to) {
        if (to == MoveOrderStatus.SCHEDULED && !order.depositPaid()) {
            throw new IllegalStateException(
                    "Move order " + order.id().value() + " cannot be scheduled: deposit is not paid");
        }
    }
}
