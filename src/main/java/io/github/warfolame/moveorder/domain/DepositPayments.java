package io.github.warfolame.moveorder.domain;

/**
 * Outbound port to the payment provider that takes the customer's deposit.
 * The domain owns this interface; the HTTP adapter will live in {@code client}.
 */
public interface DepositPayments {

    /**
     * @return {@code true} if the provider has received the deposit for this order
     * @throws RuntimeException of any kind if the provider cannot answer (timeout, 5xx)
     */
    boolean isDepositPaid(MoveOrderNumber number);
}
