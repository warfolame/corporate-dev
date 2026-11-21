package io.github.warfolame.moveorder.config;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The HS256 signing secret, bound from {@code app.jwt.secret} in the active profile's yaml:
 * a classroom value in {@code application-dev.yaml}, {@code ${CSS_JWT_SECRET}} in
 * {@code application-prod.yaml}. No secret is written in Java.
 *
 * <p>Whoever knows the secret can sign a token for any user with any role, so a short one
 * stops the application at startup instead of signing weak tokens.
 *
 * @param secret at least 32 bytes (256 bits), the key size HS256 needs
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret) {

    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("app.jwt.secret must be set (CSS_JWT_SECRET in prod)");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes (256 bits) for HS256");
        }
    }

    /** Never print the secret: this record may end up in a log line or an error report. */
    @Override
    public String toString() {
        return "JwtProperties[secret=***]";
    }
}
