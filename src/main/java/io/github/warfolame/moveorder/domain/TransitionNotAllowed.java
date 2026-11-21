package io.github.warfolame.moveorder.domain;

/**
 * A rule forbids this status change: the status table ({@link MoveOrderPolicy}) or the deposit
 * stop-factor ({@link DepositRequiredRule}). It is still the {@link IllegalStateException} of
 * Labs 1 and 2, with a name the HTTP edge can map to 409. The order does not change.
 */
public class TransitionNotAllowed extends IllegalStateException {

    public TransitionNotAllowed(String message) {
        super(message);
    }
}
