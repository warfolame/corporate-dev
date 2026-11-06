package io.github.warfolame.moveorder.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * The prod profile reads the database and the provider from environment variables.
 * Here the test supplies them; on a server they are real env vars.
 */
@SpringBootTest(properties = {
        "CSS_DB_URL=jdbc:postgresql://localhost:5432/css",
        "CSS_DB_USER=css",
        "CSS_DB_PASSWORD=from-the-environment",
        "CSS_DOWNSTREAM_URL=https://payments.example.kz"})
@ActiveProfiles("prod")
class ProdProfileTest {

    @Autowired
    private Environment environment;

    @Autowired
    private DownstreamProperties downstream;

    @Test
    void databaseComesFromEnvironmentVariables() {
        assertEquals("jdbc:postgresql://localhost:5432/css", environment.getProperty("spring.datasource.url"));
        assertEquals("css", environment.getProperty("spring.datasource.username"));
        assertEquals("from-the-environment", environment.getProperty("spring.datasource.password"));
    }

    @Test
    void downstreamComesFromAnEnvironmentVariable() {
        assertEquals(URI.create("https://payments.example.kz"), downstream.baseUrl());
    }
}
