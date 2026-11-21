package io.github.warfolame.moveorder.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;

/**
 * The prod profile reads the database, the provider and the JWT secret from environment
 * variables. Here the test supplies them; on a server they are real env vars.
 * The dev users do not exist in prod.
 */
@SpringBootTest(properties = {
        "CSS_DB_URL=jdbc:postgresql://localhost:5432/css",
        "CSS_DB_USER=css",
        "CSS_DB_PASSWORD=from-the-environment",
        "CSS_DOWNSTREAM_URL=https://payments.example.kz",
        "CSS_JWT_SECRET=prod-secret-from-the-environment-0123456789"})
@ActiveProfiles("prod")
class ProdProfileTest {

    @Autowired
    private Environment environment;

    @Autowired
    private DownstreamProperties downstream;

    @Autowired
    private JwtProperties jwt;

    @Autowired
    private ObjectProvider<UserDetailsService> users;

    @Autowired
    private AuthenticationManager authentication;

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

    @Test
    void jwtSecretComesFromCssJwtSecret() {
        assertEquals("prod-secret-from-the-environment-0123456789", jwt.secret());
    }

    @Test
    void prodShipsNoDevUsers() {
        assertNull(users.getIfAvailable());
        assertThrows(BadCredentialsException.class, () -> authentication.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("nurlan", "manager-dev")));
    }
}
