package io.github.warfolame.moveorder.client;

import io.github.warfolame.moveorder.domain.DepositPayments;
import io.github.warfolame.moveorder.domain.MoveOrderNumber;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Adapter for the {@link DepositPayments} port: the payment provider over HTTP.
 * It already knows where the provider is and how long to wait, both from yaml.
 * The real call and the timeout arrive in Lab 6; until then it never opens a socket.
 */
public final class PaymentsClient implements DepositPayments {

    private final URI baseUrl;
    private final Duration timeout;

    public PaymentsClient(URI baseUrl, Duration timeout) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    /**
     * Not connected yet. {@code DepositConfirmation} turns this into {@code PaymentsUnavailable}.
     */
    @Override
    public boolean isDepositPaid(MoveOrderNumber number) {
        throw new UnsupportedOperationException("the payment provider call arrives in Lab 6");
    }

    public URI baseUrl() {
        return baseUrl;
    }

    public Duration timeout() {
        return timeout;
    }
}
