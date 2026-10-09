package io.github.warfolame.moveorder.domain;

import java.util.Objects;

/**
 * Asks the payment provider whether the customer has paid the deposit,
 * so that {@link DepositRequiredRule} lets the order be scheduled.
 */
public final class DepositConfirmation {

    private final DepositPayments payments;

    public DepositConfirmation(DepositPayments payments) {
        this.payments = Objects.requireNonNull(payments, "payments must not be null");
    }

    /**
     * @return the order with the deposit paid if the provider has it, otherwise the same order
     * @throws PaymentsUnavailable if the provider cannot answer
     */
    public MoveOrder confirm(MoveOrder order) {
        boolean paid;
        try {
            paid = payments.isDepositPaid(order.number());
        } catch (RuntimeException e) {
            throw new PaymentsUnavailable(order.number(), e);
        }
        return paid ? order.payDeposit() : order;
    }
}
