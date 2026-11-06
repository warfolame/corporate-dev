package io.github.warfolame.moveorder.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.warfolame.moveorder.client.PaymentsClient;
import io.github.warfolame.moveorder.domain.DepositPayments;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * The dev profile: localhost database and the payment provider's URL and timeout from yaml.
 * Nothing here opens a socket to port 9090.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DevProfileTest {

    @Autowired
    private DownstreamProperties downstream;

    @Autowired
    private DepositPayments payments;

    @Autowired
    private Environment environment;

    @Test
    void downstreamComesFromTheDevYaml() {
        assertEquals(URI.create("http://localhost:9090"), downstream.baseUrl());
        assertEquals(Duration.ofSeconds(2), downstream.timeout());
    }

    @Test
    void thePaymentsAdapterGetsTheYamlValues() {
        PaymentsClient client = assertInstanceOf(PaymentsClient.class, payments);

        assertEquals(downstream.baseUrl(), client.baseUrl());
        assertEquals(downstream.timeout(), client.timeout());
    }

    @Test
    void databaseIsTheClassroomLocalhost() {
        assertEquals("jdbc:postgresql://localhost:5432/css", environment.getProperty("spring.datasource.url"));
        assertEquals("css", environment.getProperty("spring.datasource.username"));
    }

    /** Other yaml gives other values: nothing about the provider is fixed in Java. */
    @Test
    void anyBaseUrlAndDurationBind() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "app.downstream.base-url", "https://payments.example.kz",
                "app.downstream.timeout", "500ms")));

        DownstreamProperties bound = binder.bind("app.downstream", DownstreamProperties.class).get();

        assertEquals(URI.create("https://payments.example.kz"), bound.baseUrl());
        assertEquals(Duration.ofMillis(500), bound.timeout());
    }
}
