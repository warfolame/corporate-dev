package io.github.warfolame.moveorder.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the payment provider lives and how long we wait for it, bound from
 * {@code app.downstream.*} in the active profile's yaml. No URL is written in Java.
 *
 * @param baseUrl e.g. the dev value in {@code application-dev.yaml}
 * @param timeout a duration such as {@code 2s}, not a number of milliseconds
 */
@ConfigurationProperties(prefix = "app.downstream")
public record DownstreamProperties(URI baseUrl, Duration timeout) {

    public DownstreamProperties {
        Objects.requireNonNull(baseUrl, "app.downstream.base-url must be set");
        Objects.requireNonNull(timeout, "app.downstream.timeout must be set");
    }
}
