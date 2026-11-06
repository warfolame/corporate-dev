package io.github.warfolame.moveorder.config;

import io.github.warfolame.moveorder.client.PaymentsClient;
import io.github.warfolame.moveorder.domain.DepositConfirmation;
import io.github.warfolame.moveorder.domain.DepositPayments;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the outbound payments port: the yaml values in {@link DownstreamProperties}
 * go into {@link PaymentsClient}, and the port goes into {@link DepositConfirmation}.
 */
@Configuration
@EnableConfigurationProperties(DownstreamProperties.class)
public class PaymentsConfig {

    @Bean
    public DepositPayments depositPayments(DownstreamProperties downstream) {
        return new PaymentsClient(downstream.baseUrl(), downstream.timeout());
    }

    @Bean
    public DepositConfirmation depositConfirmation(DepositPayments payments) {
        return new DepositConfirmation(payments);
    }
}
