package io.github.warfolame.moveorder.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * A real token, no mocks and no web layer: {@link TokenService} signs it with a test secret,
 * and the decoder from {@link JwtConfig} reads it back with the same secret.
 */
class TokenServiceTest {

    private static final SecretKey KEY = new SecretKeySpec(
            "test-secret-at-least-32-bytes-long!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final JwtConfig config = new JwtConfig();
    private final TokenService tokens = new TokenService(config.jwtEncoder(KEY));
    private final JwtDecoder decoder = config.jwtDecoder(KEY);

    private static UsernamePasswordAuthenticationToken user(String name, String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated(name, null,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
    }

    @Test
    void issuedTokenCarriesUserRoleAndExpiry() {
        String token = tokens.issue(user("aliya", "ROLE_DISPATCHER"));

        Jwt jwt = decoder.decode(token);

        assertEquals("aliya", jwt.getSubject());
        assertEquals(List.of("DISPATCHER"), jwt.getClaimAsStringList("roles"));
        assertTrue(jwt.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void tokenIsHs256FromOurIssuerAndLivesFifteenMinutes() {
        Jwt jwt = decoder.decode(tokens.issue(user("nurlan", "ROLE_MANAGER")));

        assertEquals("HS256", jwt.getHeaders().get("alg"));
        assertEquals("move-orders", jwt.getClaimAsString("iss"));
        assertEquals(Duration.ofMinutes(15), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
        assertEquals(List.of("MANAGER"), jwt.getClaimAsStringList("roles"));
    }

    /** Spring Security 7 adds FACTOR_PASSWORD after a login; only roles go into the token. */
    @Test
    void onlyRolesGoIntoTheToken() {
        Jwt jwt = decoder.decode(tokens.issue(user("aliya", "ROLE_DISPATCHER", "FACTOR_PASSWORD")));

        assertEquals(List.of("DISPATCHER"), jwt.getClaimAsStringList("roles"));
    }

    /** Anyone can read the payload, so it holds nothing secret: these five claims and no more. */
    @Test
    void payloadHasOnlyTheFiveClaims() {
        String token = tokens.issue(user("aliya", "ROLE_DISPATCHER"));
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

        assertEquals(List.of("exp", "iat", "iss", "roles", "sub"),
                decoder.decode(token).getClaims().keySet().stream().sorted().toList());
        assertFalse(payload.contains("password"));
        assertFalse(payload.contains("ROLE_"));
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        SecretKey other = new SecretKeySpec(
                "another-secret-also-32-bytes-long!!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String forged = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(other)))
                .issue(user("aliya", "ROLE_MANAGER"));

        assertThrows(JwtException.class, () -> decoder.decode(forged));
    }

    /** A dispatcher edits roles to MANAGER in the payload: the signature no longer matches. */
    @Test
    void anEditedPayloadIsRejected() {
        String[] parts = tokens.issue(user("aliya", "ROLE_DISPATCHER")).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("DISPATCHER", "MANAGER");
        String edited = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertThrows(JwtException.class, () -> decoder.decode(edited));
    }

    @Test
    void aTokenFromAnotherProductIsRejected() {
        assertThrows(JwtException.class, () -> decoder.decode(sign("helpdesk", Instant.now().plusSeconds(600))));
    }

    @Test
    void anExpiredTokenIsRejected() {
        assertThrows(JwtException.class, () -> decoder.decode(sign(TokenService.ISSUER, Instant.now().minusSeconds(3600))));
    }

    @Test
    void aTokenWithoutExpiryIsRejected() {
        assertThrows(JwtException.class, () -> decoder.decode(sign(TokenService.ISSUER, null)));
    }

    /** The plain decoder from the lecture would accept these; ours checks iss and exp too. */
    @Test
    void theSameSecretAloneIsNotEnough() {
        String helpdesk = sign("helpdesk", Instant.now().plusSeconds(600));

        Jwt readAnyway = NimbusJwtDecoder.withSecretKey(KEY).build().decode(helpdesk);

        assertEquals("helpdesk", readAnyway.getClaimAsString("iss"));
    }

    private static String sign(String issuer, Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("aliya")
                .issuedAt(Instant.now().minusSeconds(7200))
                .claim("roles", List.of("MANAGER"));
        if (expiresAt != null) {
            claims.expiresAt(expiresAt);
        }
        return new NimbusJwtEncoder(new ImmutableSecret<>(KEY))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}
