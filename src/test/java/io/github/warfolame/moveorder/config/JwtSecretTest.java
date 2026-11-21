package io.github.warfolame.moveorder.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * One secret, outside Java: 32+ characters from yaml, CSS_JWT_SECRET in prod, nothing in
 * {@code src/main/java}, and a short value stops the application at startup.
 */
class JwtSecretTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path RESOURCES = Path.of("src/main/resources");

    private final ApplicationContextRunner jwt = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class);

    @Test
    void aShortSecretFailsAtStartup() {
        jwt.withPropertyValues("app.jwt.secret=secret").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("at least 32 bytes");
        });
    }

    @Test
    void aMissingSecretFailsAtStartup() {
        jwt.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void thirtyTwoCharactersStart() {
        jwt.withPropertyValues("app.jwt.secret=" + "x".repeat(32))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtDecoder.class));
    }

    @Test
    void thirtyOneCharactersDoNot() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("app.jwt.secret", "x".repeat(31))));

        assertThrows(BindException.class, () -> binder.bind("app.jwt", JwtProperties.class));
    }

    @Test
    void theSecretIsNeverPrinted() {
        JwtProperties properties = new JwtProperties("dev-only-move-orders-jwt-secret-change-me-0123456789");

        assertFalse(properties.toString().contains("dev-only"));
    }

    @Test
    void devSecretIsInYamlAndLongEnough() throws IOException {
        String secret = yaml("application-dev.yaml").get("app.jwt.secret");

        assertTrue(secret.length() >= 32, "dev secret has " + secret.length() + " characters");
    }

    @Test
    void prodSecretComesOnlyFromTheEnvironment() throws IOException {
        assertEquals("${CSS_JWT_SECRET}", yaml("application-prod.yaml").get("app.jwt.secret"));
    }

    @Test
    void sharedYamlHasNoSecret() throws IOException {
        assertFalse(yaml("application.yaml").containsKey("app.jwt.secret"));
    }

    /** What a reviewer does by hand: grep src/main/java for the dev secret, and for {noop}. */
    @Test
    void noSecretAndNoNoopInJava() throws IOException {
        String secret = yaml("application-dev.yaml").get("app.jwt.secret");
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains(secret), file + " contains the dev secret");
                assertFalse(source.contains("{noop}"), file + " uses {noop}");
            }
        }
        assertFalse(Files.readString(RESOURCES.resolve("application-dev.yaml")).contains("{noop}"));
    }

    private static Map<String, String> yaml(String name) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(name, new FileSystemResource(RESOURCES.resolve(name)));
        return sources.stream()
                .flatMap(source -> ((Map<?, ?>) source.getSource()).entrySet().stream())
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().toString(), entry -> entry.getValue().toString(), (a, b) -> b));
    }
}
