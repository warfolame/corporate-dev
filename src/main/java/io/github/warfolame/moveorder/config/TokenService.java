package io.github.warfolame.moveorder.config;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Writes the pass. After a successful login it puts five facts into a JWT and signs it
 * HS256 with our secret: who issued it ({@code iss}), whose it is ({@code sub}), when it was
 * issued ({@code iat}), until when it is valid ({@code exp}) and the user's {@code roles}.
 *
 * <p>The payload is signed, not encrypted: anyone can read it. So there is no password,
 * no phone and no IIN in it.
 */
@Service
public class TokenService {

    /** Our product. {@link JwtConfig} rejects a token whose {@code iss} is anything else. */
    public static final String ISSUER = "move-orders";

    /** A stolen token dies in 15 minutes; then the client logs in again. */
    public static final Duration LIFETIME = Duration.ofMinutes(15);

    /** Our own claim; {@link SecurityConfig} tells Spring to read the roles from here. */
    public static final String ROLES_CLAIM = "roles";

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtEncoder encoder;

    public TokenService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * @param user an authenticated user, e.g. from {@code AuthenticationManager}
     * @return the signed token, {@code header.payload.signature}
     */
    public String issue(Authentication user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getName())
                .issuedAt(now)
                .expiresAt(now.plus(LIFETIME))
                .claim(ROLES_CLAIM, roles(user))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * {@code ROLE_MANAGER} becomes {@code MANAGER}. Only roles go into the token: Spring Security
     * also adds factor authorities such as {@code FACTOR_PASSWORD} after a login, and those are skipped.
     */
    static List<String> roles(Authentication user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .sorted()
                .toList();
    }
}
