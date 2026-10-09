package io.github.warfolame.moveorder.domain;

/**
 * The payment provider could not tell whether the deposit is paid.
 * Our error, not the provider's stack trace: the order stays as it was.
 */
public class PaymentsUnavailable extends RuntimeException {

    public PaymentsUnavailable(MoveOrderNumber number, Throwable cause) {
        super("payment provider is unavailable for move order " + number.value(), cause);
    }
}
